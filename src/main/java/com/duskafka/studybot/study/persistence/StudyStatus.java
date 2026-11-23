package com.duskafka.studybot.study.persistence;

/**
 * 스터디 상태 ({@code studies.status}). glossary에 값이 없어 S05에서는 {@code ACTIVE}만 둔다 (S05 PR 판단 기록).
 * 스터디 종료(5.16 '위임 또는 스터디 종료 먼저')를 만드는 단계에서 값을 더한다.
 */
public enum StudyStatus {

	ACTIVE

}
