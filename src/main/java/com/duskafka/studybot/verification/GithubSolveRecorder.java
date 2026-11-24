package com.duskafka.studybot.verification;

import com.duskafka.studybot.progress.SolveToAssign;
import com.duskafka.studybot.progress.StudyDayAssignment;
import com.duskafka.studybot.verification.persistence.SolveRecordRepository;
import com.duskafka.studybot.verification.persistence.SolveStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * push 커밋에서 문제를 식별해 풀이 기록을 남긴다 (5.3 'push 처리 순서' 3·4·6, 5.4, S08 만들 것 2~6).
 *
 * <p>커밋마다
 * <ol>
 * <li>메시지와 추가·수정 파일 경로를 파서에 넣는다 ({@link ProblemParser}).</li>
 * <li>식별한 문제마다 {@code CONFIRMED} 기록을 넣는다. 같은 사용자·커밋·문제가 이미 있으면 무시한다. 새로 생겼으면 배정 연결 지점을
 * 부른다 ({@link StudyDayAssignment}, S09가 채움).</li>
 * <li>문제를 하나도 식별하지 못했고, 식별하지 못한 풀이 파일 중 경로 접두사 안의 것이 있으면 그 첫 파일로 확인 대기 기록 하나를
 * 넣는다 (커밋마다 하나, 사람이 정함, 2026-11-24 S08 세션 "커밋마다 하나"). 5.4 흐름도의 단위가 커밋 하나라서다.
 * 문제를 식별한 커밋에 남은 파일은 확인 대기로 남기지 않는다.</li>
 * </ol>
 *
 * <p>인정 시각은 넘겨받은 수신 시각이다. 처리하는 시각을 쓰지 않는다 (R3, D5). 기록에는 원본 경로와 파서 버전을 남기고, 커밋 메시지와
 * 작성자 정보는 남기지 않는다 (R16, 9.8).
 *
 * <p>호출하는 쪽의 트랜잭션 안에서만 부른다. push라면 수신함 상태(PROCESSED)와 풀이 기록, 배정이 한 트랜잭션으로 커밋되거나 함께
 * 롤백된다 (5.3 P1·P2 "처리 결과 반영과 상태 PROCESSED를 한 트랜잭션으로", pseudocode {@code recordSolve}).
 */
@Service
@RequiredArgsConstructor
public class GithubSolveRecorder {

	private final SolveRecordRepository solveRecords;

	private final StudyDayAssignment studyDayAssignment;

	@Transactional(propagation = Propagation.MANDATORY)
	public RecordedSolves record(GithubPush push) {
		PathPrefix prefix = PathPrefix.of(push.pathPrefix());
		int confirmed = 0;
		int pending = 0;
		for (GithubCommit commit : push.commits()) {
			ParsedCommit parsed = ProblemParser.parse(commit.message(), commit.changedPaths());
			if (parsed.problems().isEmpty()) {
				Optional<String> pendingPath = parsed.unidentifiedPaths().stream().filter(prefix::contains).findFirst();
				if (pendingPath.isPresent() && insertPending(push, commit, pendingPath.get())) {
					pending++;
				}
				continue;
			}
			for (IdentifiedProblem problem : parsed.problems()) {
				if (insertConfirmed(push, commit, problem)) {
					confirmed++;
				}
			}
		}
		return new RecordedSolves(confirmed, pending);
	}

	private boolean insertConfirmed(GithubPush push, GithubCommit commit, IdentifiedProblem problem) {
		ProblemRef ref = problem.ref();
		Optional<Long> id = solveRecords.insertGithubIfAbsent(push.userId(), ref.platform().name(), ref.problemNo(),
				problem.title(), push.receivedAt(), commit.sha(), push.repoId(), push.inboxId(), problem.rawPath(),
				ProblemParser.VERSION, SolveStatus.CONFIRMED.name());
		id.ifPresent(solveRecordId -> studyDayAssignment.assign(new SolveToAssign(solveRecordId, push.userId(),
				ref.platform().name(), ref.problemNo(), push.receivedAt())));
		return id.isPresent();
	}

	private boolean insertPending(GithubPush push, GithubCommit commit, String rawPath) {
		return solveRecords
			.insertGithubIfAbsent(push.userId(), null, null, null, push.receivedAt(), commit.sha(), push.repoId(),
					push.inboxId(), rawPath, ProblemParser.VERSION, SolveStatus.PENDING_IDENTIFICATION.name())
			.isPresent();
	}

}
