package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.common.time.RuleVersionCalendar;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import com.duskafka.studybot.study.service.StudyTimeline;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마감 전 알림을 만들 날을 고르고 진행 지점을 올린다 (S13 만들 것 2, 5.8, pseudocode '마감 전 알림'). notification이 부른다
 * (notification → study, architecture.md 4.2). 대상 계산은 progress가 한다.
 */
@Component
@RequiredArgsConstructor
public class ReminderDays {

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final Clock clock;

	/** 알림을 만들 스터디: 활성 스터디, ID 순서. */
	@Transactional(readOnly = true)
	public List<Long> activeStudyIds() {
		return this.studyRepository.findActiveIds();
	}

	/**
	 * 지금 알림을 만들 스터디 일. 없으면 빈 값이다.
	 *
	 * <ul>
	 * <li>날은 지금 시각이 속한 스터디 일 하나다. 그래서 마감({@code startOf(D+1)})이 지난 날은 고르지 않는다. 서버가 알림 시각부터 마감 뒤까지
	 * 꺼져 있었다면 그날 알림은 만들지 않는다 (5.8 '늦게 깨어나도 의미 있게', S13 수용 기준).</li>
	 * <li>진행 지점({@code last_reminded_day})이 그날이거나 그 뒤면 이미 만들었다.</li>
	 * <li>알림 시각({@link RuleVersionCalendar#reminderAt}: 그날 규칙으로 센 마감 − 그날 규칙의 알림 오프셋)이 아직 안 됐으면 빈 값이다. 오늘
	 * 바꾼 오프셋은 다음 스터디 일부터 적용된다 (R2, 규칙 버전).</li>
	 * </ul>
	 *
	 * <p>쿼리는 스터디·규칙 버전 한 번씩이다.
	 */
	@Transactional(readOnly = true)
	public Optional<DueReminder> due(long studyId) {
		Study study = this.studyRepository.findById(studyId)
			.orElseThrow(() -> new NotFoundException(StudyAccess.NOT_FOUND_DETAIL));
		RuleVersionCalendar calendar = StudyTimeline
			.calendar(this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId), study.zoneId());
		Instant now = this.clock.instant();
		LocalDate today = calendar.studyDayOf(now);
		LocalDate lastReminded = study.getLastRemindedDay();
		if (lastReminded != null && !lastReminded.isBefore(today)) {
			return Optional.empty();
		}
		if (now.isBefore(calendar.reminderAt(today))) {
			return Optional.empty();
		}
		return Optional.of(new DueReminder(studyId, study.getName(), study.getMode(), study.zoneId(), today));
	}

	/**
	 * 알림 생성의 진행 지점을 그날로 올린다 (pseudocode {@code study.lastRemindedDay = day}). 알림 행을 넣는 트랜잭션 안에서 부른다.
	 *
	 * @return 올렸는지. 이미 그날이거나 그 뒤면 거짓이다
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean markReminded(long studyId, LocalDate studyDay) {
		return this.studyRepository.advanceLastRemindedDay(studyId, studyDay) == 1;
	}

	/**
	 * 알림을 만들 스터디 일 하나.
	 *
	 * @param studyId 스터디 ID
	 * @param studyName 스터디 이름 (알림 문구)
	 * @param mode 스터디 모드. 혼자 쓰기는 공유가 없다 (R15)
	 * @param zoneId 스터디 시간대 (알림 문구의 마감 시각)
	 * @param studyDay 알림을 만들 스터디 일 (지금 시각이 속한 날)
	 */
	public record DueReminder(long studyId, String studyName, StudyMode mode, ZoneId zoneId, LocalDate studyDay) {

		public DueReminder {
			Objects.requireNonNull(studyName, "studyName");
			Objects.requireNonNull(mode, "mode");
			Objects.requireNonNull(zoneId, "zoneId");
			Objects.requireNonNull(studyDay, "studyDay");
		}

	}

}
