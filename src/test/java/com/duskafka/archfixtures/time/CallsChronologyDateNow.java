package com.duskafka.archfixtures.time;

import java.time.chrono.ChronoLocalDate;
import java.time.chrono.IsoChronology;

/** IsoChronology.dateNow(): 걸려야 한다. */
public class CallsChronologyDateNow {

	public ChronoLocalDate today() {
		return IsoChronology.INSTANCE.dateNow();
	}

}
