package com.duskafka.archfixtures.time;

import java.time.LocalDate;

/** LocalDate.now(): 걸려야 한다. */
public class CallsLocalDateNow {

	public LocalDate today() {
		return LocalDate.now();
	}

}
