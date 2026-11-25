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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 풀이 목록과 확인 대기 지정 (S10 만들 것 3·4, api.md 4.4). 풀이 기록은 사용자 ID로 범위를 묶어 읽고 고친다 (api.md 4.5 '주의할 것').
 */
@Service
@RequiredArgsConstructor
public class MySolveService {

	static final String NOT_FOUND_DETAIL = "풀이를 찾을 수 없어요.";

	static final String NOT_PENDING_DETAIL = "확인 대기 풀이에만 문제를 지정할 수 있어요.";

	static final String SAME_PROBLEM_IN_COMMIT_DETAIL = "같은 커밋에 이 문제의 풀이가 이미 있어요.";

	private final SolveRecordRepository solveRecords;

	private final StudyDayAssignment studyDayAssignment;

	/**
	 * 내 풀이. 확인 대기를 포함하고, 수신 시각이 늦은 것부터다.
	 *
	 * @param from 이 시각부터 (포함). {@code null}이면 처음부터
	 * @param to 이 시각 전까지 (뺌). {@code null}이면 끝까지
	 */
	@Transactional(readOnly = true)
	public Page<MySolveRow> mySolves(long userId, @Nullable Instant from, @Nullable Instant to, int page, int size) {
		return this.solveRecords.findMine(userId, from, to, PageRequest.of(page, size));
	}

	/**
	 * 확인 대기 풀이에 문제를 지정하고, 원래 수신 시각으로 스터디 일에 배정한다 (R6, T26). 한 트랜잭션이다.
	 *
	 * <ol>
	 * <li>내 풀이 기록을 잠근다. 없거나 남의 것이면 404 (T19). 확인 대기가 아니면 409.</li>
	 * <li>같은 커밋에 그 문제의 기록이 이미 있으면 409 (UNIQUE 위반으로 500이 나지 않게).</li>
	 * <li>문제를 채우고 {@code CONFIRMED}로 바꾼다.</li>
	 * <li>지금 속한 스터디마다 원래 수신 시각으로 배정한다 (R14 '늦게 배정하는 풀이도 배정할 때의 멤버십으로'). 어느 스터디에서든 배정할
	 * 날이 판정됐으면 409이고 3번까지 모두 롤백된다 ({@link StudyDayAssignment#assignIdentified}).</li>
	 * </ol>
	 *
	 * @return 바뀐 풀이
	 */
	@Transactional
	public MySolveRow identify(long userId, long solveId, ProblemRef problem) {
		String status = this.solveRecords.lockStatus(solveId, userId)
			.orElseThrow(() -> new NotFoundException(NOT_FOUND_DETAIL));
		if (!SolveStatus.PENDING_IDENTIFICATION.name().equals(status)) {
			throw new ConflictException(NOT_PENDING_DETAIL);
		}
		String platform = problem.platform().name();
		if (this.solveRecords.existsSameProblemInCommit(solveId, platform, problem.problemNo())) {
			throw new ConflictException(SAME_PROBLEM_IN_COMMIT_DETAIL);
		}
		if (this.solveRecords.identify(solveId, platform, problem.problemNo()) != 1) {
			throw new IllegalStateException("잠근 확인 대기 기록을 바꾸지 못했어요: " + solveId);
		}
		MySolveRow identified = this.solveRecords.findRow(solveId)
			.orElseThrow(() -> new IllegalStateException("바꾼 풀이 기록이 없어요: " + solveId));
		this.studyDayAssignment.assignIdentified(
				new SolveToAssign(solveId, userId, platform, problem.problemNo(), identified.receivedAt()));
		return identified;
	}

}
