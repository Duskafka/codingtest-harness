package com.duskafka.studybot.study;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 스터디 하나의 스터디 일 하루에 대해 현황을 보여 주는 데 필요한 값 ({@link StudyDays}, S10). 시각은 규칙 버전에 걸친 달력으로 센다 (R2).
 *
 * @param studyId 스터디 ID
 * @param studyDay 그 스터디 일
 * @param today 지금 시각이 속한 스터디 일
 * @param startsAt 그 스터디 일이 시작하는 시각 (전날 규칙의 기준 시각)
 * @param endsAt 그 스터디 일이 끝나는 시각 = 다음 날이 시작하는 시각 (마감)
 * @param graceEndsAt 그날의 늦은 풀이를 받아 줄 수 있는 유예 구간이 끝나는 시각 (R4. 다음 날 규칙의 유예)
 * @param dailyGoal 그날 규칙의 하루 목표
 * @param restDay 그날 규칙의 쉬는 요일인지
 * @param exemptionsPerSeason 그날 규칙의 기수당 면제권. 한 기수 안에서는 같다 (R2, S11 남은 면제권)
 * @param penaltyType 그날 규칙의 벌금 방식. 다음 기수부터 바뀌는 항목이라 한 기수 안에서는 같다 (R2, S12 정산 표시)
 * @param fineAmount 그날 규칙의 벌금액(원). 한 기수 안에서는 같다 (R2)
 * @param season 그날을 담은 기수 행의 기간. 기수 밖이면 {@code null} (R9, OD-07)
 * @param members 그날 멤버였던 사용자들 (가입 순서). 그날 가입했거나 그날 탈퇴한 사람도 들어 있다 (R14)
 */
public record StudyDayContext(long studyId, LocalDate studyDay, LocalDate today, Instant startsAt, Instant endsAt,
		Instant graceEndsAt, int dailyGoal, boolean restDay, int exemptionsPerSeason, PenaltyType penaltyType,
		int fineAmount, MemberStudy.@Nullable SeasonPeriod season, List<RosterMember> members) {

	/**
	 * 그날 멤버였던 사용자 하나.
	 *
	 * @param userId 사용자 ID
	 * @param role 역할 (같은 날 다시 가입했으면 나중 멤버십의 역할)
	 * @param judged 이 사용자를 그날 판정하는지 (R9 '판정 대상': 판정 시작일 이후, 탈퇴한 날까지, 기수 안)
	 * @param active 지금 탈퇴하지 않은 멤버십이 있는지 (같은 날 다시 가입했으면 참). 마감 전 알림은 탈퇴한 멤버에게 보내지 않는다 (S13, 사람이 정함
	 * 2026-11-27). 판정은 이 값을 보지 않는다 (탈퇴한 날까지 판정, R14)
	 */
	public record RosterMember(long userId, MembershipRole role, boolean judged, boolean active) {

		public RosterMember {
			Objects.requireNonNull(role, "role");
		}

	}

	public StudyDayContext {
		Objects.requireNonNull(studyDay, "studyDay");
		Objects.requireNonNull(today, "today");
		Objects.requireNonNull(startsAt, "startsAt");
		Objects.requireNonNull(endsAt, "endsAt");
		Objects.requireNonNull(graceEndsAt, "graceEndsAt");
		Objects.requireNonNull(penaltyType, "penaltyType");
		members = List.copyOf(members);
	}

	/** 그날을 담은 기수 행의 ID. 기수 밖이면 {@code null}이다. */
	public @Nullable Long seasonId() {
		return this.season == null ? null : this.season.seasonId();
	}

	/** 그날이 기수 안인지. 기수 밖인 날은 판정하지 않는다 (R9, OD-07). */
	public boolean inSeason() {
		return this.season != null;
	}

}
