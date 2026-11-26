package com.duskafka.studybot.verification;

import com.duskafka.studybot.progress.SolveDetail;
import com.duskafka.studybot.progress.SolveRecordDetails;
import com.duskafka.studybot.verification.persistence.SolveRecordRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * progress의 조회 포트({@link SolveRecordDetails})를 풀이 기록으로 구현한다 (S10 현황의 '최근 풀이'). 의존 방향은 verification →
 * progress 그대로다 (architecture.md 4.2).
 */
@Component
@RequiredArgsConstructor
class SolveRecordDetailsProvider implements SolveRecordDetails {

	private final SolveRecordRepository solveRecords;

	@Override
	@Transactional(readOnly = true)
	public List<SolveDetail> detailsOf(Collection<Long> solveRecordIds) {
		if (solveRecordIds.isEmpty()) {
			return List.of();
		}
		return this.solveRecords.findRows(solveRecordIds)
			.stream()
			.map(row -> new SolveDetail(row.id(), row.source().name(), row.title(), row.receivedAt()))
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Long> countableSolveIds(long userId, String platform, String problemNo, Instant receivedFrom,
			Instant receivedTo) {
		return this.solveRecords.findCountableIds(userId, platform, problemNo, receivedFrom, receivedTo);
	}

}
