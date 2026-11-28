package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import java.time.ZoneId;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스터디의 이름·모드·방장·시간대 (S14 공유 카드·디스코드 게시의 제목과 마감 표기). notification이 부른다 (notification → study,
 * architecture.md 4.2). 권한 검사는 하지 않는다. 부르는 쪽이 먼저 {@link StudyAccess}로 한다.
 */
@Component
@RequiredArgsConstructor
public class StudyProfiles {

	private final StudyRepository studyRepository;

	/** 스터디 하나. 없으면 404. 쿼리는 스터디 한 번이다. */
	@Transactional(readOnly = true)
	public StudyProfile of(long studyId) {
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException(StudyAccess.NOT_FOUND_DETAIL));
		return new StudyProfile(study.getId(), study.getName(), study.getMode(), study.getOwnerId(), study.zoneId());
	}

	/**
	 * 스터디 하나의 공개 값.
	 *
	 * @param studyId 스터디 ID
	 * @param name 스터디 이름 (사용자가 정한 이름)
	 * @param mode 스터디 모드. 혼자 쓰기는 공유가 없다 (R15)
	 * @param ownerUserId 방장 사용자 ID
	 * @param zoneId 스터디 시간대
	 */
	public record StudyProfile(long studyId, String name, StudyMode mode, long ownerUserId, ZoneId zoneId) {

		public StudyProfile {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(zoneId, "zoneId");
		}

	}

}
