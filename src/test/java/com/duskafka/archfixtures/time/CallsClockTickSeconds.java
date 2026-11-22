package com.duskafka.archfixtures.time;

import java.time.Clock;
import java.time.ZoneOffset;

/** Clock.tickSeconds(ZoneId): 시스템 시계 기반이라 걸려야 한다. */
public class CallsClockTickSeconds {

	public Clock clock() {
		return Clock.tickSeconds(ZoneOffset.UTC);
	}

}
