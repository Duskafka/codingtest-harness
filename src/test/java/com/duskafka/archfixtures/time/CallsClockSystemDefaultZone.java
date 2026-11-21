package com.duskafka.archfixtures.time;

import java.time.Clock;

/** Clock.systemDefaultZone(): 시스템 시계는 Clock 설정 클래스에서만 만든다. */
public class CallsClockSystemDefaultZone {

	public Clock clock() {
		return Clock.systemDefaultZone();
	}

}
