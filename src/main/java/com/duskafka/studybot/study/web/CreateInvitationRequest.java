package com.duskafka.studybot.study.web;

import com.duskafka.studybot.study.service.StudyLimits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;

/**
 * {@code POST /api/studies/{id}/invitations} 요청. 본문은 없어도 된다.
 *
 * @param maxUses 사용 횟수 1~30. 없으면 30
 */
record CreateInvitationRequest(@Nullable @Min(1) @Max(StudyLimits.MAX_INVITATION_USES) Integer maxUses) {

	int maxUsesOrDefault() {
		return this.maxUses != null ? this.maxUses : StudyLimits.MAX_INVITATION_USES;
	}

}
