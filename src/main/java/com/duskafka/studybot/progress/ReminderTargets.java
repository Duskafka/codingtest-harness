package com.duskafka.studybot.progress;

import com.duskafka.studybot.progress.persistence.MemberDayRepository;
import com.duskafka.studybot.progress.persistence.MemberDayRow;
import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.StudyDayContext;
import com.duskafka.studybot.study.StudyDayContext.RosterMember;
import com.duskafka.studybot.study.StudyDays;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 전 알림의 대상 (S13 만들 것 2·3, 5.8, T14·T16). notification이 알림을 만들 때와 보내기 직전에 부른다 (notification → progress,
 * architecture.md 4.2).
 *
 * <p>대상은 그날 '남음'인 멤버다. 오늘 현황과 같은 계산({@link MemberDayProgress#evaluate})이라 판정 대상이 아닌 멤버(가입한 날, 기수 밖, R9),
 * 쉬는 날, 목표를 채운 멤버, 면제권을 쓴 멤버가 빠진다 (pseudocode '마감 전 알림'의 {@code judgeTargets ... where not rest day and
 * exemption not used and solvedCount < goal}). 면제권을 쓴 멤버를 빼는 것은 별도 통지가 아니라 이 재확인으로 한다 (알려진 충돌 10번).
 *
 * <p>지금 탈퇴한 멤버(그날 다시 가입하지 않음)도 뺀다. 탈퇴한 뒤 들어온 풀이는 그날에도 세지 않으니(R14) 알려도 결과를 바꿀 수 없다 (사람이 정함,
 * 2026-11-27 S13 '탈퇴한 멤버에게 보내지 않음'). 판정은 그대로 그날까지 한다.
 *
 * <p>쿼리는 그날의 맥락(스터디·규칙·기수·멤버십) 4와 멤버 일 1이다. 멤버 수와 상관없다.
 */
@Component
@RequiredArgsConstructor
public class ReminderTargets {

	private final StudyDays studyDays;

	private final MemberDayRepository memberDays;

	/**
	 * 그날 아직 목표를 채우지 못한 멤버들.
	 *
	 * @param studyDay 스터디 일. 오늘이나 지난 날이어야 한다 ({@link StudyDays#load})
	 */
	@Transactional(readOnly = true)
	public ReminderDay remainingOn(long studyId, LocalDate studyDay) {
		StudyDayContext day = this.studyDays.load(studyId, studyDay);
		Map<Long, MemberDayRow> rows = rowsOf(day);
		List<Remaining> remaining = day.members()
			.stream()
			.filter(member -> isRemaining(day, member, rows.get(member.userId())))
			.map(member -> new Remaining(member.userId(), day.dailyGoal() - solvedCount(rows.get(member.userId()))))
			.toList();
		Long ownerUserId = day.members()
			.stream()
			.filter(member -> member.role() == MembershipRole.OWNER)
			.map(RosterMember::userId)
			.findFirst()
			.orElse(null);
		return new ReminderDay(studyId, studyDay, day.endsAt(), ownerUserId, remaining);
	}

	/**
	 * 그 멤버가 그날 아직 '남음'인지. 발송 직전에 다시 확인한다 (5.8 '발송 직전 다시 확인', T16). 그새 풀었거나, 면제권을 썼거나, 탈퇴했으면
	 * 거짓이다.
	 */
	@Transactional(readOnly = true)
	public boolean stillRemaining(long studyId, long userId, LocalDate studyDay) {
		StudyDayContext day = this.studyDays.load(studyId, studyDay);
		Map<Long, MemberDayRow> rows = rowsOf(day);
		return day.members()
			.stream()
			.filter(member -> member.userId() == userId)
			.anyMatch(member -> isRemaining(day, member, rows.get(userId)));
	}

	private Map<Long, MemberDayRow> rowsOf(StudyDayContext day) {
		return this.memberDays.findDayRows(day.studyId(), day.studyDay())
			.stream()
			.collect(Collectors.toMap(MemberDayRow::userId, Function.identity()));
	}

	private static boolean isRemaining(StudyDayContext day, RosterMember member, @Nullable MemberDayRow row) {
		if (!member.active()) {
			return false;
		}
		boolean exemptionUsed = row != null && row.exemptionUsedAt() != null;
		return MemberDayProgress.evaluate(member.judged(), day.restDay(), day.dailyGoal(), solvedCount(row),
				exemptionUsed)
			.status() == MemberDayStatus.REMAINING;
	}

	private static int solvedCount(@Nullable MemberDayRow row) {
		return row == null ? 0 : row.solvedCount();
	}

	/**
	 * 알림을 만들 하루.
	 *
	 * @param studyId 스터디 ID
	 * @param studyDay 스터디 일
	 * @param deadline 그날이 끝나는 시각(= 다음 스터디 일의 시작). 알림의 만료 시각이다 (5.8)
	 * @param ownerUserId 그날 멤버였던 방장. 없으면 {@code null}
	 * @param remaining 아직 목표를 채우지 못한 멤버 (가입 순서)
	 */
	public record ReminderDay(long studyId, LocalDate studyDay, Instant deadline, @Nullable Long ownerUserId,
			List<Remaining> remaining) {

		public ReminderDay {
			Objects.requireNonNull(studyDay, "studyDay");
			Objects.requireNonNull(deadline, "deadline");
			remaining = List.copyOf(remaining);
		}

	}

	/**
	 * 아직 목표를 채우지 못한 멤버 하나.
	 *
	 * @param userId 사용자 ID
	 * @param problemsLeft 목표까지 남은 문제 수 (1 이상)
	 */
	public record Remaining(long userId, int problemsLeft) {

	}

}
