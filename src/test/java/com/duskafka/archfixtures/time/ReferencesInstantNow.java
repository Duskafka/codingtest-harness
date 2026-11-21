package com.duskafka.archfixtures.time;

import java.time.Instant;
import java.util.function.Supplier;

/** Instant::now 메서드 참조: 호출이 아니어도 걸려야 한다. */
public class ReferencesInstantNow {

	public Supplier<Instant> supplier() {
		return Instant::now;
	}

}
