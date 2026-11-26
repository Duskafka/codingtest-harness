package com.duskafka.studybot.progress;

import java.time.LocalDate;
import java.util.Map;

/**
 * 스터디에서 센 수동 인증 수를 읽는 조회 포트 (S11 만들 것 4 '멤버별 수동 인증 비율'). 수동 인증은 verification에 있고 progress는
 * verification을 의존하지 않아서, 인터페이스를 progress에 두고 verification이 구현한다 ({@link SolveRecordDetails}와 같은 방식).
 */
public interface SelfReportCounts {

	/**
	 * 기간 안의 스터디 일에 낸, 지금 인정된(센) 수동 인증 수를 사용자마다. 쿼리 한 번이다. 없는 사용자는 빠진다.
	 *
	 * @param fromDay 첫 스터디 일 (포함)
	 * @param toDay 마지막 스터디 일 (포함)
	 */
	Map<Long, Long> acceptedByUser(long studyId, LocalDate fromDay, LocalDate toDay);

}
