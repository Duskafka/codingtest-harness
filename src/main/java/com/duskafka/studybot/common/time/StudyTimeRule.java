package com.duskafka.studybot.common.time;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * 스터디 일 계산에 필요한 시간 규칙 값 (docs/spec/domain-rules.md R1, 3.3). {@link StudyCalendar}가 이 값만 받는다.
 *
 * <p>study 모듈의 규칙(버전)에서 시간 관련 항목만 꺼낸 값이다. {@code common}은 study 엔티티를 모르므로
 * 호출하는 쪽이 그날 적용되는 규칙에서 이 값을 만들어 넘긴다 (architecture.md '알려진 충돌과 빈 곳' 4번).
 * R1의 범위를 벗어나면 만들 수 없다.
 *
 * @param zoneId 스터디 시간대
 * @param dayBoundary 하루 기준 시각. 00:00~06:00 (양 끝 포함)
 * @param graceMinutes 유예(분). 0~120
 * @param reminderOffsetMinutes 알림 오프셋(분). 기준 시각 몇 분 전에 알릴지. 30~360
 * @param restDays 쉬는 요일. 없으면 빈 집합
 */
public record StudyTimeRule(ZoneId zoneId, LocalTime dayBoundary, int graceMinutes, int reminderOffsetMinutes,
		Set<DayOfWeek> restDays) {

	/** 하루 기준 시각의 최댓값 (R1). 최솟값은 00:00이라 따로 검사하지 않는다. */
	public static final LocalTime MAX_DAY_BOUNDARY = LocalTime.of(6, 0);

	public static final int MIN_GRACE_MINUTES = 0;

	public static final int MAX_GRACE_MINUTES = 120;

	public static final int MIN_REMINDER_OFFSET_MINUTES = 30;

	public static final int MAX_REMINDER_OFFSET_MINUTES = 360;

	public StudyTimeRule {
		Objects.requireNonNull(zoneId, "zoneId");
		Objects.requireNonNull(dayBoundary, "dayBoundary");
		Objects.requireNonNull(restDays, "restDays");
		if (dayBoundary.isAfter(MAX_DAY_BOUNDARY)) {
			throw new IllegalArgumentException("하루 기준 시각은 00:00~06:00이어야 해요: " + dayBoundary);
		}
		requireInRange("유예(분)", graceMinutes, MIN_GRACE_MINUTES, MAX_GRACE_MINUTES);
		requireInRange("알림 오프셋(분)", reminderOffsetMinutes, MIN_REMINDER_OFFSET_MINUTES,
				MAX_REMINDER_OFFSET_MINUTES);
		restDays = Set.copyOf(restDays);
	}

	/** 유예. 기준 시각부터 실제로 흐른 시간으로 센다. */
	public Duration grace() {
		return Duration.ofMinutes(graceMinutes);
	}

	/** 알림 오프셋. 다음 기준 시각에서 실제로 흐른 시간만큼 거슬러 센다. */
	public Duration reminderOffset() {
		return Duration.ofMinutes(reminderOffsetMinutes);
	}

	private static void requireInRange(String name, int value, int min, int max) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(name + "은 " + min + "~" + max + "이어야 해요: " + value);
		}
	}

}
