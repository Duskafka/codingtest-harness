package com.duskafka.studybot.study.web;

import com.duskafka.studybot.study.service.StudyLimits;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * {@code PATCH /api/studies/{id}} 요청 (api.md 4.4 '이름, 방장 위임'). 둘 중 하나는 있어야 한다.
 *
 * @param name 새 이름. 앞뒤 공백을 빼고 1~50자
 * @param ownerUserId 방장을 넘길 멤버의 사용자 ID
 */
record UpdateStudyRequest(@Nullable @Size(min = 1, max = StudyLimits.NAME_MAX_LENGTH) String name,
		@Nullable Long ownerUserId) {

	UpdateStudyRequest {
		name = name != null ? name.strip() : null;
	}

}
