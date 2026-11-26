package com.duskafka.studybot.verification;

import com.duskafka.studybot.progress.SelfReportCounts;
import com.duskafka.studybot.verification.persistence.SelfReportRepository;
import com.duskafka.studybot.verification.persistence.UserReportCount;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * progress의 조회 포트({@link SelfReportCounts})를 수동 인증으로 구현한다 (S11 현황의 수동 인증 비율). 의존 방향은 verification →
 * progress 그대로다 (architecture.md 4.2).
 */
@Component
@RequiredArgsConstructor
class SelfReportCountsProvider implements SelfReportCounts {

	private final SelfReportRepository selfReports;

	@Override
	@Transactional(readOnly = true)
	public Map<Long, Long> acceptedByUser(long studyId, LocalDate fromDay, LocalDate toDay) {
		return this.selfReports.countAcceptedByUser(studyId, fromDay, toDay)
			.stream()
			.collect(Collectors.toMap(UserReportCount::userId, UserReportCount::count));
	}

}
