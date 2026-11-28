package com.duskafka.studybot.progress;

import com.duskafka.studybot.auth.UserDirectory;
import com.duskafka.studybot.auth.UserProfile;
import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRow;
import com.duskafka.studybot.study.StudyDayContext;
import com.duskafka.studybot.study.StudyDayContext.RosterMember;
import com.duskafka.studybot.study.StudyDays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디 일 하루의 멤버별 상태와 판정 결과를 이름과 함께 준다 (S14 공유 카드·디스코드 현황·결과 게시). notification이 부른다
 * (notification → progress, architecture.md 4.2). 권한 검사는 하지 않는다. 부르는 쪽이 먼저 {@code StudyAccess}로 한다.
 *
 * <p>멤버와 상태는 오늘 현황({@code GET /api/studies/{id}/today}, S10)과 같은 계산이다: 그날 멤버였던 사람(그날 탈퇴한 사람 포함, R14),
 * {@link MemberDayProgress#evaluate}. 카드의 링크가 여는 화면과 숫자가 같게 하려는 것이다 (S14 PR 판단 기록). 벌금 값은 주지 않는다
 * (5.9 '벌금 금액은 기본으로 빼고', 사람이 정함 2026-11-28 'S14는 늘 뺌').
 *
 * <p>쿼리는 그날의 맥락(스터디·규칙·기수·멤버십) 4, 멤버 일 1, 사용자 1이다. 멤버 수와 상관없다. 여러 문장을 한 스냅샷에서 읽도록
 * {@code REPEATABLE_READ}다 (오늘 현황과 같은 이유). 부르는 쪽 트랜잭션이 있으면 거기에 들어간다.
 */
@Component
@RequiredArgsConstructor
public class StudyDayBoards {

	private final StudyDays studyDays;

	private final MemberDayRepository memberDays;

	private final UserDirectory userDirectory;

	/**
	 * 스터디 일 하루의 현황.
	 *
	 * @param studyDay 스터디 일. {@code null}이면 오늘. 오늘이나 지난 날이어야 한다 ({@link StudyDays#load})
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public StudyDayBoard of(long studyId, @Nullable LocalDate studyDay) {
		StudyDayContext day = this.studyDays.load(studyId, studyDay);
		Map<Long, MemberDayRow> rows = this.memberDays.findDayRows(studyId, day.studyDay())
			.stream()
			.collect(Collectors.toMap(MemberDayRow::userId, Function.identity()));
		Map<Long, String> logins = this.userDirectory
			.profilesOf(day.members().stream().map(RosterMember::userId).toList())
			.stream()
			.collect(Collectors.toMap(UserProfile::userId, UserProfile::login));
		List<BoardMember> members = day.members().stream().map(member -> {
			MemberDayRow row = rows.get(member.userId());
			int solvedCount = row == null ? 0 : row.solvedCount();
			boolean exemptionUsed = row != null && row.exemptionUsedAt() != null;
			MemberDayStatus status = MemberDayProgress
				.evaluate(member.judged(), day.restDay(), day.dailyGoal(), solvedCount, exemptionUsed)
				.status();
			return new BoardMember(member.userId(), logins.getOrDefault(member.userId(), ""), status,
					row == null ? null : row.result());
		}).toList();
		return new StudyDayBoard(studyId, day.studyDay(), day.endsAt(), day.restDay(), day.inSeason(), members);
	}

	/**
	 * 그날 판정 결과별 멤버 수 (S14 결과 알림). 멤버 일 행만 한 번 읽는다. 날짜 검사(아직 오지 않은 날 등)를 하지 않아서, 진행 지점을 올리는
	 * 트랜잭션 안의 결과 알림이 이 조회 때문에 실패해 판정을 막는 일이 없다 (결정 제안서 010의 단점을 줄이려는 것, S14 PR 판단 기록).
	 */
	@Transactional(readOnly = true)
	public Map<JudgmentResult, Integer> resultCountsOf(long studyId, LocalDate studyDay) {
		Map<JudgmentResult, Integer> counts = new EnumMap<>(JudgmentResult.class);
		for (MemberDayRow row : this.memberDays.findDayRows(studyId, studyDay)) {
			if (row.result() != null) {
				counts.merge(row.result(), 1, Integer::sum);
			}
		}
		return counts;
	}

	/**
	 * 스터디 일 하루의 현황.
	 *
	 * @param studyId 스터디 ID
	 * @param studyDay 그 스터디 일
	 * @param endsAt 그날이 끝나는 시각 (마감)
	 * @param restDay 그날 규칙의 쉬는 요일인지
	 * @param inSeason 그날이 기수 안인지 (기수 밖이면 판정하지 않음, R9)
	 * @param members 그날 멤버였던 사람들 (가입 순서)
	 */
	public record StudyDayBoard(long studyId, LocalDate studyDay, Instant endsAt, boolean restDay, boolean inSeason,
			List<BoardMember> members) {

		public StudyDayBoard {
			Objects.requireNonNull(studyDay, "studyDay");
			Objects.requireNonNull(endsAt, "endsAt");
			members = List.copyOf(members);
		}

		/** 이 상태인 멤버들 (가입 순서). */
		public List<BoardMember> withStatus(MemberDayStatus status) {
			return this.members.stream().filter(member -> member.status() == status).toList();
		}

		/** 이 판정 결과인 멤버들 (가입 순서). */
		public List<BoardMember> withResult(JudgmentResult result) {
			return this.members.stream().filter(member -> member.result() == result).toList();
		}

		/** 판정 결과별 멤버 수. */
		public Map<JudgmentResult, Integer> resultCounts() {
			Map<JudgmentResult, Integer> counts = new EnumMap<>(JudgmentResult.class);
			this.members.stream()
				.filter(member -> member.result() != null)
				.forEach(member -> counts.merge(member.result(), 1, Integer::sum));
			return counts;
		}

	}

	/**
	 * 멤버 하나.
	 *
	 * @param userId 사용자 ID
	 * @param login GitHub 로그인명 (따로 정하는 이름이 없다)
	 * @param status 지금 센 수로 정한 그날 상태
	 * @param result 그날 판정 결과. 아직 판정하지 않았으면 {@code null}
	 */
	public record BoardMember(long userId, String login, MemberDayStatus status, @Nullable JudgmentResult result) {

		public BoardMember {
			Objects.requireNonNull(login, "login");
			Objects.requireNonNull(status, "status");
		}

	}

}
