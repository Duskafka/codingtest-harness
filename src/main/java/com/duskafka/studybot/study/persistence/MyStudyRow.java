package com.duskafka.studybot.study.persistence;

import com.duskafka.studybot.study.MembershipRole;
import com.duskafka.studybot.study.StudyMode;

/** 내 스터디 목록의 한 줄 (조회 전용 DTO 쿼리, OD-04 기본안). */
public record MyStudyRow(long id, String name, StudyMode mode, MembershipRole role) {
}
