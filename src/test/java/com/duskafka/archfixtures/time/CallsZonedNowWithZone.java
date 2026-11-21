package com.duskafka.archfixtures.time;

import java.time.ZoneId;
import java.time.ZonedDateTime;

/** ZonedDateTime.now(ZoneId): 시간대를 넘겨도 시스템 시계라 걸려야 한다. */
public class CallsZonedNowWithZone {

	public ZonedDateTime nowInSeoul() {
		return ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
	}

}
