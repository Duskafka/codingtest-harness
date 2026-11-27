package com.duskafka.studybot.judgment;

import com.duskafka.studybot.progress.JudgmentResult;
import com.duskafka.studybot.study.JudgingDay;
import com.duskafka.studybot.study.JudgingDays;
import com.duskafka.studybot.study.StudySeasons;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 일일 판정 배치 (S12 만들 것 2~5·8, 5.10, R9). 스케줄은 {@link JudgmentSchedule}이 건다.
 *
 * <p>스터디마다 차례로:
 * <ol>
 * <li>기수 준비 ({@link StudySeasons#prepare}): 시작일이 된 기수를 진행으로, 다음 기수 행을 예정으로, 정산이 끝난 기수를 종료로 (5.10 처리
 * 순서 4). 판정보다 먼저 해서 따라잡는 날이 기수 행 없이 기수 밖으로 건너뛰어지지 않게 한다. 실패하면 그 스터디는 이번 실행에서 판정하지
 * 않는다.</li>
 * <li>판정 시각이 지난 날을 오래된 순서로 하루씩 ({@link JudgingDays#nextDue}). '어제'가 아니라 아직 판정하지 않은 날을 전부 판정한다.</li>
 * <li>그날 판정 대상마다 트랜잭션을 따로 ({@link MemberJudge}). 한 멤버에서 오류가 나도 다른 멤버는 판정한다.</li>
 * <li>그날 오류가 없을 때만 진행 지점을 올린다 ({@link JudgingDays#markJudged}). 오류가 있으면 그 스터디는 멈추고 다음 실행에 그날부터 다시
 * 한다(이미 판정한 멤버는 건너뜀). 날을 건너뛰어 판정하지 않는다.</li>
 * </ol>
 * 한 스터디의 오류는 다른 스터디를 막지 않는다. 결과 알림(DAILY_RESULT)은 S14이고 judgment에서 notification을 부르지 않는다
 * (알려진 충돌 2번).
 *
 * <p>지표 (5.10 '지표', metrics.md 10.2 '판정 배치 소요 시간, 따라잡은 날 수'): {@code studybot.judgment.batch}(배치 한 번의 소요 시간), {@code studybot.judgment.days}(진행
 * 지점을 올린 날 수 = 따라잡은 날 포함), {@code studybot.judgment.results}(결과별 판정 수, {@code result} 태그. 실패 판정은
 * {@code result=FAIL}), {@code studybot.judgment.errors}(오류 수). 배치 한 번마다 요약 로그도 남긴다.
 */
@Slf4j
@Component
public class DailyJudgmentBatch {

	private final JudgingDays judgingDays;

	private final StudySeasons studySeasons;

	private final MemberJudge memberJudge;

	private final MeterRegistry meterRegistry;

	private final Timer batchTimer;

	private final Counter dayCounter;

	private final Counter errorCounter;

	DailyJudgmentBatch(JudgingDays judgingDays, StudySeasons studySeasons, MemberJudge memberJudge,
			MeterRegistry meterRegistry) {
		this.judgingDays = judgingDays;
		this.studySeasons = studySeasons;
		this.memberJudge = memberJudge;
		this.meterRegistry = meterRegistry;
		this.batchTimer = Timer.builder("studybot.judgment.batch")
			.description("판정 배치 한 번의 소요 시간")
			.register(meterRegistry);
		this.dayCounter = Counter.builder("studybot.judgment.days")
			.description("진행 지점을 올린 날 수 (따라잡은 날 포함)")
			.register(meterRegistry);
		this.errorCounter = Counter.builder("studybot.judgment.errors")
			.description("판정 배치의 오류 수 (멤버 판정, 기수 준비, 판정할 날 조회)")
			.register(meterRegistry);
	}

	/** 활성 스터디를 모두 판정한다. */
	public JudgmentSummary run() {
		Timer.Sample sample = Timer.start(this.meterRegistry);
		JudgmentSummary total = JudgmentSummary.EMPTY;
		for (long studyId : this.judgingDays.activeStudyIds()) {
			total = total.plus(judgeStudy(studyId));
		}
		long nanos = sample.stop(this.batchTimer);
		if (total.judgedDays() > 0 || total.errors() > 0) {
			log.info("판정 배치: 스터디 {}개, 판정한 날 {}일, 판정한 멤버 일 {}개, 오류 {}건, {}ms", total.studies(), total.judgedDays(),
					total.judgedMembers(), total.errors(), nanos / 1_000_000);
		}
		else {
			log.debug("판정 배치: 스터디 {}개, 판정할 날 없음, {}ms", total.studies(), nanos / 1_000_000);
		}
		return total;
	}

	/** 스터디 하나의 기수를 준비하고 판정 시각이 지난 날을 모두 판정한다. */
	public JudgmentSummary judgeStudy(long studyId) {
		try {
			this.studySeasons.prepare(studyId);
		}
		catch (RuntimeException ex) {
			log.error("기수 준비에 실패해 이번 실행에서 판정하지 않아요: study={}", studyId, ex);
			this.errorCounter.increment();
			return new JudgmentSummary(1, 0, 0, 1);
		}
		int days = 0;
		int members = 0;
		int errors = 0;
		while (true) {
			Optional<JudgingDay> next;
			try {
				next = this.judgingDays.nextDue(studyId);
			}
			catch (RuntimeException ex) {
				log.error("판정할 날을 고르지 못했어요: study={}", studyId, ex);
				errors++;
				break;
			}
			if (next.isEmpty()) {
				break;
			}
			JudgingDay day = next.get();
			int dayErrors = 0;
			for (long userId : day.targetUserIds()) {
				try {
					Optional<JudgmentResult> result = this.memberJudge.judge(day, userId);
					if (result.isPresent()) {
						members++;
						this.meterRegistry.counter("studybot.judgment.results", "result", result.get().name()).increment();
					}
				}
				catch (RuntimeException ex) {
					log.error("멤버 판정에 실패했어요: study={}, day={}, user={}", studyId, day.studyDay(), userId, ex);
					dayErrors++;
				}
			}
			errors += dayErrors;
			if (dayErrors > 0) {
				log.warn("오류가 있어 진행 지점을 올리지 않고 멈춰요: study={}, day={}, 오류 {}건", studyId, day.studyDay(), dayErrors);
				break;
			}
			if (!this.judgingDays.markJudged(studyId, day.studyDay())) {
				log.warn("진행 지점이 이미 그날 뒤라 멈춰요: study={}, day={}", studyId, day.studyDay());
				break;
			}
			days++;
		}
		this.dayCounter.increment(days);
		this.errorCounter.increment(errors);
		return new JudgmentSummary(1, days, members, errors);
	}

}
