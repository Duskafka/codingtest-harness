package com.duskafka.archfixtures.time;

import java.util.GregorianCalendar;
import java.util.TimeZone;

/** new GregorianCalendar(), new GregorianCalendar(TimeZone): 현재 시각으로 만들어져서 걸려야 한다. */
public class CreatesGregorianCalendar {

	public GregorianCalendar calendar() {
		return new GregorianCalendar();
	}

	public GregorianCalendar calendarIn(TimeZone zone) {
		return new GregorianCalendar(zone);
	}

}
