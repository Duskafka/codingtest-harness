package com.duskafka.archfixtures.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Clock을 상속한 클래스 안에서 한정자 없이 systemUTC()를 불러도 걸려야 한다. */
public class SubclassedClockSystemUtc extends Clock {

	private final Clock delegate = systemUTC();

	@Override
	public ZoneId getZone() {
		return delegate.getZone();
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return delegate.withZone(zone);
	}

	@Override
	public Instant instant() {
		return delegate.instant();
	}

}
