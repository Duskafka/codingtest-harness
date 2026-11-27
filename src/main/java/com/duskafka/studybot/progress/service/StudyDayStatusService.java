package com.duskafka.studybot.progress.service;

import com.duskafka.studybot.auth.UserDirectory;
import com.duskafka.studybot.auth.UserProfile;
import com.duskafka.studybot.progress.MemberDayProgress;
import com.duskafka.studybot.progress.SelfReportCounts;
import com.duskafka.studybot.progress.SolveDetail;
import com.duskafka.studybot.progress.SolveRecordDetails;
import com.duskafka.studybot.progress.persistence.CountedSolveRow;
import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRepository.UserCount;
import com.duskafka.studybot.progress.persistence.MemberDayRow;
import com.duskafka.studybot.progress.persistence.StudyDaySolveRepository;
import com.duskafka.studybot.progress.service.StudyDayStatus.MemberStatus;
import com.duskafka.studybot.progress.service.StudyDayStatus.RecentSolve;
import com.duskafka.studybot.study.MemberStudy.SeasonPeriod;
import com.duskafka.studybot.study.PenaltyType;
import com.duskafka.studybot.study.StudyAccess;
import com.duskafka.studybot.study.StudyDayContext;
import com.duskafka.studybot.study.StudyDayContext.RosterMember;
import com.duskafka.studybot.study.StudyDays;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 현황과 특정 날 상세 (S10 만들 것 1·2·5, api.md 4.4). 멤버별 완료·남음·면제와 최근 풀이를 준다.
 *
 * <p>스터디·날짜 단위로 한 번에 가져온다 (architecture.md 6장 ⑤): 권한 1, 그날의 맥락(스터디·규칙·기수·멤버십) 4, 멤버 일 1, 집계 1,
 * 사용자 1, 풀이 기록 1. 기수 안인 날은 쓴 면제권 1 (S11 남은 면제권), 벌금 방식이 있으면 실패 횟수 1 (S12 정산 표시), 보는 사람이
 * 방장이면 센 풀이 1과 수동 인증 1 (S11 수동 인증 비율)을 더한다. 멤버 수와 상관없이 같다 (S10 수용 기준 2). 엔티티를 내보내지 않고 조회용 DTO로 읽는다.
 *
 * <p>여러 문장을 한 스냅샷에서 읽도록 {@code REPEATABLE_READ}로 읽는다. PostgreSQL 기본(READ COMMITTED)은 문장마다 새 스냅샷이라, 그
 * 사이에 배정이 커밋되면 멤버 일(센 수)과 최근 풀이가 서로 어긋날 수 있다 (CodeRabbit PR #26 리뷰). 읽기만 해서 직렬화 실패는 없다.
 */
@Service
@RequiredArgsConstructor
public class StudyDayStatusService {

	/** 최근 풀이를 몇 개까지 줄지. 큰 응답을 만들지 않는다 (9.4 Supabase egress). S10 PR 판단 기록. */
	public static final int RECENT_SOLVES_LIMIT = 50;

	private final StudyAccess studyAccess;

	private final StudyDays studyDays;

	private final MemberDayRepository memberDays;

	private final StudyDaySolveRepository studyDaySolves;

	private final UserDirectory userDirectory;

	private final SolveRecordDetails solveRecordDetails;

	private final SelfReportCounts selfReportCounts;

	/** 오늘 현황. 멤버가 아니면 404. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public StudyDayStatus today(long studyId, long userId) {
		boolean owner = this.studyAccess.requireMember(studyId, userId).isOwner();
		return statusOf(this.studyDays.load(studyId, null), owner);
	}

	/** 특정 날 상세. 멤버가 아니면 404, 아직 오지 않은 날이나 스터디가 생기기 전 날이면 400 ({@link StudyDays#load}). */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public StudyDayStatus day(long studyId, long userId, LocalDate studyDay) {
		boolean owner = this.studyAccess.requireMember(studyId, userId).isOwner();
		return statusOf(this.studyDays.load(studyId, studyDay), owner);
	}

	/**
	 * @param owner 보는 사람이 방장인지. 방장에게만 멤버별 수동 인증 비율을 준다 (S11 만들 것 4)
	 */
	private StudyDayStatus statusOf(StudyDayContext day, boolean owner) {
		Map<Long, MemberDayRow> rows = this.memberDays.findDayRows(day.studyId(), day.studyDay())
			.stream()
			.collect(Collectors.toMap(MemberDayRow::userId, Function.identity()));
		List<CountedSolveRow> counted = this.studyDaySolves.findLatest(day.studyId(), day.studyDay(),
				Limit.of(RECENT_SOLVES_LIMIT));
		Set<Long> userIds = new LinkedHashSet<>();
		day.members().forEach(member -> userIds.add(member.userId()));
		counted.forEach(solve -> userIds.add(solve.userId()));
		Map<Long, UserProfile> profiles = this.userDirectory.profilesOf(userIds)
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, Function.identity()));
		SeasonStats seasonStats = seasonStatsOf(day, owner);
		List<MemberStatus> members = day.members()
			.stream()
			.map(member -> memberStatus(day, member, rows.get(member.userId()), profileOf(profiles, member.userId()),
					seasonStats))
			.toList();
		return new StudyDayStatus(day, members, recentSolves(counted, profiles));
	}

	private static MemberStatus memberStatus(StudyDayContext day, RosterMember member, @Nullable MemberDayRow row,
			UserProfile profile, SeasonStats seasonStats) {
		int solvedCount = row == null ? 0 : row.solvedCount();
		boolean exemptionUsed = row != null && row.exemptionUsedAt() != null;
		MemberDayProgress progress = MemberDayProgress.evaluate(member.judged(), day.restDay(), day.dailyGoal(),
				solvedCount, exemptionUsed);
		Integer failCount = seasonStats.failCount(member.userId());
		return new MemberStatus(member.userId(), profile.login(), profile.avatarUrl(), member.role(), progress,
				exemptionUsed, solvedCount, row == null ? null : row.result(),
				seasonStats.exemptionsRemaining(member.userId(), day.exemptionsPerSeason()),
				seasonStats.selfReportRatio(member.userId()), failCount,
				failCount == null ? null : failCount * day.fineAmount());
	}

	/**
	 * 그날이 속한 기수에서 센 값들. 기수 밖이면 비어 있다. 쓴 면제권은 모든 멤버에게, 센 풀이와 수동 인증 수는 방장에게만 읽는다. 실패 횟수는
	 * 벌금 방식이 '없음'이 아닐 때만 모든 멤버에게 읽는다 (S12 만들 것 7, 사람이 정함 2026-11-27 "모든 멤버에게 멤버마다").
	 */
	private SeasonStats seasonStatsOf(StudyDayContext day, boolean owner) {
		SeasonPeriod season = day.season();
		if (season == null) {
			return SeasonStats.OUTSIDE_SEASON;
		}
		Map<Long, Long> usedExemptions = byUser(this.memberDays.countUsedExemptionsByUser(day.studyId(),
				season.startDay(), season.endDay()));
		Map<Long, Long> failures = day.penaltyType() == PenaltyType.NONE ? null
				: byUser(this.memberDays.countFailuresByUser(day.studyId(), season.startDay(), season.endDay()));
		if (!owner) {
			return new SeasonStats(true, usedExemptions, null, null, failures);
		}
		Map<Long, Long> countedSolves = byUser(
				this.studyDaySolves.countByUser(day.studyId(), season.startDay(), season.endDay()));
		Map<Long, Long> selfReports = this.selfReportCounts.acceptedByUser(day.studyId(), season.startDay(),
				season.endDay());
		return new SeasonStats(true, usedExemptions, countedSolves, selfReports, failures);
	}

	private static Map<Long, Long> byUser(List<UserCount> counts) {
		return counts.stream().collect(Collectors.toMap(UserCount::getUserId, UserCount::getCount));
	}

	/**
	 * 기수 하나에서 멤버마다 센 값.
	 *
	 * @param inSeason 그날이 기수 안인지
	 * @param usedExemptions 쓴 면제권 (성공으로 돌려받은 것은 뺌)
	 * @param countedSolves 센 풀이. 방장이 아니면 {@code null}
	 * @param selfReports 센 수동 인증. 방장이 아니면 {@code null}
	 * @param failures 실패로 판정된 날 수. 벌금 방식이 '없음'이면 {@code null}
	 */
	private record SeasonStats(boolean inSeason, Map<Long, Long> usedExemptions,
			@Nullable Map<Long, Long> countedSolves, @Nullable Map<Long, Long> selfReports,
			@Nullable Map<Long, Long> failures) {

		static final SeasonStats OUTSIDE_SEASON = new SeasonStats(false, Map.of(), null, null, null);

		@Nullable Integer failCount(long userId) {
			if (this.failures == null) {
				return null;
			}
			return Math.toIntExact(this.failures.getOrDefault(userId, 0L));
		}

		@Nullable Integer exemptionsRemaining(long userId, int perSeason) {
			if (!this.inSeason) {
				return null;
			}
			return ExemptionService.remainingOf(perSeason, this.usedExemptions.getOrDefault(userId, 0L));
		}

		@Nullable Double selfReportRatio(long userId) {
			if (this.countedSolves == null || this.selfReports == null) {
				return null;
			}
			long counted = this.countedSolves.getOrDefault(userId, 0L);
			if (counted == 0) {
				return null;
			}
			return (double) this.selfReports.getOrDefault(userId, 0L) / counted;
		}

	}

	/** 최근 배정한 풀이들에 출처·제목·수신 시각을 붙이고 수신 시각이 늦은 것부터 늘어놓는다. */
	private List<RecentSolve> recentSolves(List<CountedSolveRow> counted, Map<Long, UserProfile> profiles) {
		if (counted.isEmpty()) {
			return List.of();
		}
		Map<Long, SolveDetail> details = this.solveRecordDetails
			.detailsOf(counted.stream().map(CountedSolveRow::solveRecordId).toList())
			.stream()
			.collect(Collectors.toMap(SolveDetail::solveRecordId, Function.identity()));
		return counted.stream().map(solve -> {
			SolveDetail detail = details.get(solve.solveRecordId());
			if (detail == null) {
				throw new IllegalStateException("집계의 풀이 기록이 없어요: " + solve.solveRecordId());
			}
			return new RecentSolve(solve.userId(), profileOf(profiles, solve.userId()).login(), solve.platform(), solve.problemNo(), detail.title(), detail.source(),
					detail.receivedAt());
		}).sorted(Comparator.comparing(RecentSolve::receivedAt).reversed()).toList();
	}

	private static UserProfile profileOf(Map<Long, UserProfile> profiles, long userId) {
		UserProfile profile = profiles.get(userId);
		if (profile == null) {
			throw new IllegalStateException("사용자가 없어요: " + userId);
		}
		return profile;
	}

}
