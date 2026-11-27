package com.duskafka.studybot.study;

import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.common.time.StudyCalendar;
import com.duskafka.studybot.study.persistence.Season;
import com.duskafka.studybot.study.persistence.SeasonRepository;
import com.duskafka.studybot.study.persistence.Study;
import com.duskafka.studybot.study.persistence.StudyRepository;
import com.duskafka.studybot.study.persistence.StudyRule;
import com.duskafka.studybot.study.persistence.StudyRuleRepository;
import com.duskafka.studybot.study.service.StudyTimeline;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기수 시작 처리와 정산 확정 (S12 만들 것 6, R13, 5.13, 5.10 처리 순서 4). 판정 배치(judgment)가 판정 전에 스터디마다 부른다
 * (judgment → study, architecture.md 4.2).
 */
@Component
@RequiredArgsConstructor
public class StudySeasons {

	private final StudyRepository studyRepository;

	private final StudyRuleRepository ruleRepository;

	private final SeasonRepository seasonRepository;

	private final Clock clock;

	/**
	 * 스터디의 기수 행을 오늘에 맞춘다. 다시 불러도 안전하다.
	 *
	 * <ol>
	 * <li>마지막 기수 행이 시작했으면(시작일 ≤ 오늘) 다음 기수 행을 예정으로 만든다. 마지막 행의 시작일이 오늘보다 뒤가 될 때까지 되풀이한다.
	 * 서버가 기수 길이보다 오래 꺼져 있었어도 판정할 날마다 기수 행이 있게 하려는 것이다. 그래서 늘 한 기수 앞서 있고, 새 기수 첫날이 시작되는
	 * 순간부터 기수 안이다 (5.13). 시작일은 마지막 행의 끝 + 1(OD-07), 길이는 그 시작일에 적용되는 규칙 버전(다음 기수부터 적용하는 변경
	 * 포함, R2), 끝 날짜는 {@link StudyCalendar#seasonEndDay}로 센다.</li>
	 * <li>시작일이 된 예정 기수를 진행으로 바꾼다.</li>
	 * <li>정산 중이 된 지 72시간이 지난 기수를 종료로 바꾼다 (사람이 정함, 2026-11-27 "실제 판정 시각 + 72시간").</li>
	 * </ol>
	 *
	 * <p>오늘은 규칙 버전을 반영해 센 스터디 일이다. 스터디 행을 잠그고 한다 (결정 제안서 006). 규칙 변경이 다음 기수 행의 끝 날짜를 다시 세는
	 * 것과 겹치지 않게 하려는 것이다. 판정과 따로 짧은 트랜잭션이다: 판정과 묶으면 따라잡는 동안 초대 수락·규칙 변경이 기다린다.
	 * {@code (study_id, seq)} UNIQUE가 마지막 방어선이다.
	 *
	 * @throws IllegalStateException 규칙 버전이 없을 때
	 */
	@Transactional
	public void prepare(long studyId) {
		Study study = this.studyRepository.findByIdForUpdate(studyId)
			.orElseThrow(() -> new NotFoundException(StudyAccess.NOT_FOUND_DETAIL));
		List<Season> seasons = new ArrayList<>(this.seasonRepository.findByStudyIdOrderBySeqAsc(studyId));
		if (seasons.isEmpty()) {
			return;
		}
		List<StudyRule> rules = this.ruleRepository.findByStudyIdOrderByEffectiveFromAsc(studyId);
		Instant now = this.clock.instant();
		LocalDate today = StudyTimeline.calendar(rules, study.zoneId()).studyDayOf(now);
		Season last = seasons.getLast();
		while (!last.getStartDay().isAfter(today)) {
			LocalDate start = last.getEndDay().plusDays(1);
			int length = StudyTimeline.ruleOn(rules, start).getSeasonLengthDays();
			last = this.seasonRepository
				.save(new Season(studyId, last.getSeq() + 1, start, StudyCalendar.seasonEndDay(start, length), now));
			seasons.add(last);
		}
		for (Season season : seasons) {
			season.activateIfStarted(today);
			season.closeIfSettled(now);
		}
	}

}
