package com.duskafka.studybot.verification.service;

import com.duskafka.studybot.common.error.ConflictException;
import com.duskafka.studybot.common.error.NotFoundException;
import com.duskafka.studybot.progress.SolveToAssign;
import com.duskafka.studybot.progress.StudyDayAssignment;
import com.duskafka.studybot.verification.ProblemRef;
import com.duskafka.studybot.verification.persistence.MySolveRow;
import com.duskafka.studybot.verification.persistence.SolveRecordRepository;
import com.duskafka.studybot.verification.persistence.SolveStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 풀이 목록과 확인 대기 지정 (S10 만들 것 3·4, api.md 4.4). 풀이 기록은 사용자 ID로 범위를 묶어 읽고 고친다 (api.md 4.5 '주의할 것').
 *
 * <p>확인 대기 지정의 잠금 순서: 풀이 기록 행 → 멤버 일 행들(결정 제안서 007의 전역 순서). 풀이 기록과 멤버 일을 함께 잠그는 다른 흐름
 * (수동 인증 철회 S11 등)도 이 순서를 따라야 교착이 나지 않는다 (S10 PR 판단 기록).
 */
@Service
@RequiredArgsConstructor
public class MySolveService {

	static final String NOT_FOUND_DETAIL = "풀이를 찾을 수 없어요.";

	static final String NOT_PENDING_DETAIL = "확인 대기 풀이에만 문제를 지정할 수 있어요.";

	/** 확인 대기가 아닐 때의 409 {@code code} (사람이 정함, 2026-11-26 "code 속성 추가"). */
	public static final String NOT_PENDING_CODE = "SOLVE_NOT_PENDING";

	static final String SAME_PROBLEM_IN_COMMIT_DETAIL = "같은 커밋에 이 문제의 풀이가 이미 있어요.";

	/** 같은 커밋에 같은 문제가 이미 있을 때의 409 {@code code}. */
	public static final String SAME_PROBLEM_IN_COMMIT_CODE = "PROBLEM_ALREADY_IN_COMMIT";

	private final SolveRecordRepository solveRecords;

	private final StudyDayAssignment studyDayAssignment;

	/**
	 * 내 풀이. 확인 대기를 포함하고, 수신 시각이 늦은 것부터다.
	 *
	 * @param from 이 시각부터 (포함). {@code null}이면 처음부터
	 * @param to 이 시각 전까지 (뺌). {@code null}이면 끝까지
	 * @param status 이 상태만 (예: 확인 대기). {@code null}이면 모두 (사람이 정함, 2026-11-26 "status 필터 추가")
	 */
	@Transactional(readOnly = true)
	public Page<MySolveRow> mySolves(long userId, @Nullable Instant from, @Nullable Instant to,
			@Nullable SolveStatus status, int page, int size) {
		return this.solveRecords.findMine(userId, from, to, status, PageRequest.of(page, size));
	}

	/**
	 * 확인 대기 풀이에 문제를 지정하고, 원래 수신 시각으로 스터디 일에 배정한다 (R6, T26). 한 트랜잭션이다.
	 *
	 * <ol>
	 * <li>내 풀이 기록을 잠근다. 없거나 남의 것이면 404 (T19). 확인 대기가 아니면 409.</li>
	 * <li>같은 커밋에 그 문제의 기록이 이미 있으면 409 (UNIQUE 위반으로 500이 나지 않게). 확인 뒤에 동시에 지정된 경우는 UPDATE의 UNIQUE
	 * 위반을 409로 바꾼다.</li>
	 * <li>문제를 채우고 {@code CONFIRMED}로 바꾼다.</li>
	 * <li>지금 속한 스터디마다 원래 수신 시각으로 배정한다 (R14 '늦게 배정하는 풀이도 배정할 때의 멤버십으로'). 어느 스터디에서든 받은
	 * 시각 기준으로 배정했을 날이 판정됐으면 409이고 3번까지 모두 롤백된다 ({@link StudyDayAssignment#assignIdentified}).</li>
	 * </ol>
	 *
	 * @return 바뀐 풀이
	 */
	@Transactional
	public MySolveRow identify(long userId, long solveId, ProblemRef problem) {
		String status = this.solveRecords.lockStatus(solveId, userId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		if (!SolveStatus.PENDING_IDENTIFICATION.name().equals(status)) {
			throw new ConflictException(NOT_PENDING_CODE, NOT_PENDING_DETAIL);
		}
		String platform = problem.platform().name();
		if (this.solveRecords.existsSameProblemInCommit(solveId, platform, problem.problemNo())) {
			throw new ConflictException(SAME_PROBLEM_IN_COMMIT_CODE, SAME_PROBLEM_IN_COMMIT_DETAIL);
		}
		int changed;
		try {
			changed = this.solveRecords.identify(solveId, userId, platform, problem.problemNo());
		}
		catch (DataIntegrityViolationException ex) {
			// 앞의 확인 뒤에 같은 커밋의 다른 확인 대기가 같은 문제로 먼저 지정됐다 (CodeRabbit PR #26 리뷰). 트랜잭션은 롤백된다.
			throw new ConflictException(SAME_PROBLEM_IN_COMMIT_CODE, SAME_PROBLEM_IN_COMMIT_DETAIL);
		}
		if (changed != 1) {
			throw new IllegalStateException("잠근 확인 대기 기록을 바꾸지 못했어요: " + solveId);
		}
		MySolveRow identified = this.solveRecords.findRow(solveId, userId)
			.orElseThrow(() -> new IllegalStateException("바꾼 풀이 기록이 없어요: " + solveId));
		this.studyDayAssignment.assignIdentified(
				new SolveToAssign(solveId, userId, platform, problem.problemNo(), identified.receivedAt()));
		return identified;
	}

}
