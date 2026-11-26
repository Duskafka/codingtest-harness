package com.duskafka.studybot.progress.service;

import com.duskafka.studybot.common.error.BadRequestException;
import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRepository.LockedMemberDay;
import com.duskafka.studybot.study.MemberStudies;
import com.duskafka.studybot.study.MemberStudy;
import com.duskafka.studybot.study.MemberStudy.SeasonPeriod;
import com.duskafka.studybot.study.MemberStudyRules;
import com.duskafka.studybot.study.StudyAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 면제권 사용·취소 (R8, 5.7, pseudocode '면제권 사용', S11 만들 것 5·6).
 *
 * <ul>
 * <li>오늘(지금 시각이 속한 스터디 일)에만 쓰고 취소한다 (OD-09, 사람이 정함 2026-11-26 "당일만"). 오늘이면 그날 기준 시각 전이다.</li>
 * <li>한 트랜잭션에서 멤버십 행 → 멤버 일 행 순서로 잠근다. 면제권은 날짜마다 멤버 일 행이 달라 그 잠금만으로는 마지막 면제권 경쟁을 못
 * 막는다 (5.7, T12). 당일만이라 서로 다른 날짜의 경쟁은 기준 시각 경계에서 생긴다: 경계 직전 그날 요청이 끝나기 전에 경계 직후 다음 날 요청이
 * 들어오는 경우다.</li>
 * <li>이번 기수에 쓴 개수는 기수 행의 기간에서 센다. 성공으로 판정된 날은 면제권을 돌려받았으니 세지 않는다 (R8).</li>
 * <li>지금 시각은 요청을 받을 때 한 번 읽는다. 잠금을 기다리는 사이 경계가 지나도 받은 시각의 오늘로 본다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExemptionService {

	static final String NOT_FOUND_DETAIL = "스터디를 찾을 수 없어요.";

	static final String NOT_TODAY_DETAIL = "면제권은 오늘만, 그날 기준 시각 전까지 쓰고 취소할 수 있어요.";

	static final String REST_DAY_DETAIL = "쉬는 날이라 면제권을 쓰지 않아도 돼요.";

	static final String NOT_JUDGED_DETAIL = "판정하지 않는 날(가입한 날, 기수 밖)이라 면제권을 쓰지 않아도 돼요.";

	static final String JUDGED_DAY_DETAIL = "이미 판정이 끝난 날이에요.";

	/** 그날 목표를 이미 채웠을 때의 409 {@code code}. */
	public static final String GOAL_MET_CODE = "GOAL_ALREADY_MET";

	static final String GOAL_MET_DETAIL = "오늘 목표를 이미 채워서 면제권을 쓸 수 없어요.";

	/** 이번 기수의 면제권을 다 썼을 때의 409 {@code code}. */
	public static final String LIMIT_REACHED_CODE = "EXEMPTION_LIMIT_REACHED";

	static final String LIMIT_REACHED_DETAIL = "이번 기수의 면제권을 모두 썼어요.";

	/** 판정된 날의 409 {@code code}. 오늘만 다루니 보통은 생기지 않는다. */
	static final String JUDGED_DAY_CODE = "STUDY_DAY_JUDGED";

	private final StudyAccess studyAccess;

	private final MemberStudies memberStudies;

	private final MemberDayRepository memberDays;

	private final Clock clock;

	/**
	 * 오늘 면제권을 쓴다. 이미 썼으면 그대로 두고 같은 상태를 돌려준다 (같은 날 두 번은 한 번만 반영).
	 *
	 * <ul>
	 * <li>멤버가 아니면 404 (T19).</li>
	 * <li>오늘이 아니면, 쉬는 날이면, 판정하지 않는 날(가입한 날, 기수 밖)이면 400.</li>
	 * <li>그날 목표를 이미 채웠으면 409 {@value #GOAL_MET_CODE}, 이번 기수 한도를 다 썼으면 409 {@value #LIMIT_REACHED_CODE}.</li>
	 * </ul>
	 */
	@Transactional
	public ExemptionResult use(long studyId, long userId, LocalDate studyDay) {
		Instant now = this.clock.instant();
		this.studyAccess.lockMember(studyId, userId);
		MemberStudyRules rules = rulesOf(studyId, userId);
		MemberStudy study = rules.study();
		requireToday(study, studyDay, now);
		if (study.isRestDay(studyDay)) {
			throw new BadRequestException(REST_DAY_DETAIL);
		}
		SeasonPeriod season = study.seasonOn(studyDay);
		if (season == null || !study.isJudgedOn(studyDay)) {
			throw new BadRequestException(NOT_JUDGED_DETAIL);
		}
		int perSeason = rules.termsOn(studyDay).exemptionsPerSeason();
		this.memberDays.insertIfAbsent(studyId, userId, studyDay, season.seasonId());
		LockedMemberDay row = this.memberDays.lock(studyId, userId, studyDay)
			.orElseThrow(() -> new IllegalStateException("만든 멤버 일 행을 찾지 못했어요: study=" + studyId));
		if (row.getJudged()) {
			throw new ConflictException(JUDGED_DAY_CODE, JUDGED_DAY_DETAIL);
		}
		if (!row.getExemptionUsed()) {
			if (row.getSolvedCount() >= study.dailyGoalOn(studyDay)) {
				throw new ConflictException(GOAL_MET_CODE, GOAL_MET_DETAIL);
			}
			if (usedIn(season, studyId, userId) >= perSeason) {
				throw new ConflictException(LIMIT_REACHED_CODE, LIMIT_REACHED_DETAIL);
			}
			this.memberDays.markExemptionUsed(row.getId(), now);
		}
		return new ExemptionResult(studyDay, true, perSeason, remaining(season, perSeason, studyId, userId));
	}

	/**
	 * 오늘 쓴 면제권을 취소한다 (R8 '기준 시각 전까지 취소'). 쓰지 않았으면 바꾸는 것 없이 지금 상태를 돌려준다. 내 멤버 일만 고친다 (T19).
	 * 멤버가 아니면 404, 오늘이 아니면 400.
	 */
	@Transactional
	public ExemptionResult cancel(long studyId, long userId, LocalDate studyDay) {
		Instant now = this.clock.instant();
		this.studyAccess.lockMember(studyId, userId);
		MemberStudyRules rules = rulesOf(studyId, userId);
		MemberStudy study = rules.study();
		requireToday(study, studyDay, now);
		LockedMemberDay row = this.memberDays.lock(studyId, userId, studyDay).orElse(null);
		if (row != null && row.getExemptionUsed()) {
			if (row.getJudged()) {
				throw new ConflictException(JUDGED_DAY_CODE, JUDGED_DAY_DETAIL);
			}
			this.memberDays.clearExemption(row.getId());
		}
		int perSeason = rules.termsOn(studyDay).exemptionsPerSeason();
		SeasonPeriod season = study.seasonOn(studyDay);
		return new ExemptionResult(studyDay, false, perSeason,
				season == null ? null : remaining(season, perSeason, studyId, userId));
	}

	private MemberStudyRules rulesOf(long studyId, long userId) {
		return this.memberStudies.rulesOf(studyId, userId).orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
	}

	/** 고른 날이 지금 시각이 속한 스터디 일인지 (OD-09). */
	private static void requireToday(MemberStudy study, LocalDate studyDay, Instant now) {
		if (!studyDay.equals(study.studyDayOf(now))) {
			throw new BadRequestException(NOT_TODAY_DETAIL);
		}
	}

	private long usedIn(SeasonPeriod season, long studyId, long userId) {
		return this.memberDays.countUsedExemptions(studyId, userId, season.startDay(), season.endDay());
	}

	private @Nullable Integer remaining(SeasonPeriod season, int perSeason, long studyId, long userId) {
		return remainingOf(perSeason, usedIn(season, studyId, userId));
	}

	/** 남은 면제권: 기수당 개수 − 쓴 개수. 규칙에서 기수당 개수를 줄였어도 0 아래로 내려가지 않는다. */
	static int remainingOf(int perSeason, long used) {
		return (int) Math.max(0, perSeason - used);
	}

}
