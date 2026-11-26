package com.duskafka.studybot.verification.service;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.progress.SelfReportCounting;
import com.duskafka.studybot.progress.SelfReportToCount;
import com.duskafka.studybot.verification.persistence.SelfReport;
import com.duskafka.studybot.verification.persistence.SelfReportRef;
import com.duskafka.studybot.verification.persistence.SelfReportRepository;
import com.duskafka.studybot.verification.persistence.SelfReportStatus;
import com.duskafka.studybot.verification.persistence.SolveRecordRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수동 인증 제출과 본인 철회 (R7, 5.6, S11 만들 것 1~3). 기본 '바로 인정'만 만든다 (알려진 충돌 6번).
 *
 * <p>수동 인증은 요청한 스터디의 고른 날에만 센다 (사람이 정함, 2026-11-26 S11 세션 "그 스터디의 고른 날만"). 스터디에 관한 확인과 세는 일은
 * progress가 한다 ({@link SelfReportCounting}). verification은 study에 의존하지 않는다 (architecture.md 4.2).
 *
 * <p>잠금 순서: 제출은 그날 멤버 일 행 하나만 잠근다. 철회는 수동 인증 행 → 풀이 기록 행 → 멤버 일 행이다 (S10 '풀이 기록 → 멤버 일').
 */
@Service
@RequiredArgsConstructor
public class SelfReportService {

	/** 한 사람이 한 스터디의 하루(스터디 일)에 낼 수 있는 수동 인증 수 (R7). */
	public static final int DAILY_LIMIT = 5;

	/** 하루 5건을 넘었을 때의 409 {@code code} (T29). */
	public static final String DAILY_LIMIT_CODE = "SELF_REPORT_DAILY_LIMIT";

	static final String DAILY_LIMIT_DETAIL = "수동 인증은 하루에 " + DAILY_LIMIT + "건까지만 할 수 있어요.";

	/** 인정 상태가 아닌(이미 철회한) 수동 인증을 철회할 때의 409 {@code code}. */
	public static final String NOT_ACCEPTED_CODE = "SELF_REPORT_NOT_ACCEPTED";

	static final String NOT_ACCEPTED_DETAIL = "이미 철회했거나 인정되지 않은 수동 인증이에요.";

	static final String NOT_FOUND_DETAIL = "수동 인증을 찾을 수 없어요.";

	private final SolveRecordRepository solveRecords;

	private final SelfReportRepository selfReports;

	private final SelfReportCounting selfReportCounting;

	private final Clock clock;

	/**
	 * 수동 인증을 받아 바로 센다 (R7 '바로 인정'). 한 트랜잭션이다.
	 *
	 * <ol>
	 * <li>풀이 기록(출처 {@code SELF_REPORT}, 인정 시각 = 제출 시각, R3)을 넣는다.</li>
	 * <li>그 스터디의 고른 날에 센다. 멤버가 아니면 404, 허용된 날이 아니면 400, '승인 필요'·판정된 날·이미 센 문제면 409
	 * ({@link SelfReportCounting#count}). 이때 그날 멤버 일 행을 잠근다.</li>
	 * <li>잠근 뒤 그날 낸 수동 인증 수를 세어 5건이면 409 {@value #DAILY_LIMIT_CODE}. 예외면 1·2도 함께 롤백된다.</li>
	 * <li>수동 인증 행(인정)을 넣는다.</li>
	 * </ol>
	 */
	@Transactional
	public SelfReport submit(long studyId, long userId, NewSelfReport report) {
		Instant now = this.clock.instant();
		String platform = report.problem().platform().name();
		String problemNo = report.problem().problemNo();
		long solveRecordId = this.solveRecords.insertSelfReport(userId, platform, problemNo, now);
		this.selfReportCounting.count(new SelfReportToCount(studyId, userId, report.studyDay(), now, solveRecordId,
				platform, problemNo));
		if (this.selfReports.countByStudyIdAndUserIdAndStudyDay(studyId, userId, report.studyDay()) >= DAILY_LIMIT) {
			throw new ConflictException(DAILY_LIMIT_CODE, DAILY_LIMIT_DETAIL);
		}
		return this.selfReports.save(SelfReport.accepted(studyId, userId, report.studyDay(),
				report.problem().platform(), problemNo, report.evidenceUrl(), report.memo(), solveRecordId, now));
	}

	/**
	 * 내 수동 인증을 판정 전에 철회한다 (R7, 3.4). 한 트랜잭션이다.
	 *
	 * <ol>
	 * <li>이 스터디의 내 수동 인증을 잠근다. 없거나 남의 것이거나 다른 스터디의 것이면 404 (T19). 인정 상태가 아니면 409.</li>
	 * <li>풀이 기록을 {@code IGNORED}로 바꾼다.</li>
	 * <li>그날 집계에서 뺀다. 지금 멤버가 아니면 404, 그날이 판정됐으면 409 ({@link SelfReportCounting#uncount}). 예외면 모두 롤백된다.</li>
	 * <li>수동 인증을 {@code WITHDRAWN}으로 바꾼다.</li>
	 * </ol>
	 */
	@Transactional
	public void withdraw(long studyId, long userId, long reportId) {
		Instant now = this.clock.instant();
		String status = this.selfReports.lockStatus(reportId, studyId, userId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		if (!SelfReportStatus.ACCEPTED.name().equals(status)) {
			throw new ConflictException(NOT_ACCEPTED_CODE, NOT_ACCEPTED_DETAIL);
		}
		SelfReportRef ref = this.selfReports.findRef(reportId)
			.orElseThrow(() -> new IllegalStateException("잠근 수동 인증을 찾지 못했어요: " + reportId));
		Long solveRecordId = ref.solveRecordId();
		if (solveRecordId == null) {
			throw new IllegalStateException("인정된 수동 인증에 풀이 기록이 없어요: " + reportId);
		}
		this.solveRecords.ignoreSelfReport(solveRecordId, userId);
		this.selfReportCounting.uncount(studyId, userId, ref.studyDay(), solveRecordId);
		this.selfReports.withdraw(reportId, userId, now);
	}

}
