package com.duskafka.studybot.study.service;

import java.time.Duration;

/** S05의 고정값. 스펙에 정해진 값과, 정해지지 않아 S05 PR 판단 기록에 남긴 값. */
public final class StudyLimits {

	/** 정원 (5.1 'MVP는 30명'). 방장 포함. */
	public static final int CAPACITY = 30;

	/** 초대 만료 (5.1 '기본 7일'). S05에서는 바꿀 수 없다 (S05 PR 판단 기록). */
	public static final Duration INVITATION_TTL = Duration.ofDays(7);

	/** 초대 하나의 사용 횟수 최댓값과 기본값. 정원보다 클 이유가 없어 정원과 같다 (S05 PR 판단 기록). */
	public static final int MAX_INVITATION_USES = CAPACITY;

	/**
	 * 시작일을 고를 수 있는 마지막 날: 오늘 스터디 일 + 이 일수. 첫날은 다음 스터디 일이다.
	 * 사람이 정했다 (2026-11-23, S05 세션 "다음 날~30일 뒤").
	 */
	public static final int START_DATE_MAX_DAYS_AHEAD = 30;

	/** 스터디 이름 길이 (앞뒤 공백을 뺀 글자 수). 스펙에 없어 S05 PR 판단 기록에 남겼다. */
	public static final int NAME_MAX_LENGTH = 50;

	private StudyLimits() {
	}

}
