package com.duskafka.archfixtures.time;

import java.util.Calendar;

/** Calendar.getInstance(): 걸려야 한다. */
public class CallsCalendarGetInstance {

	public Calendar calendar() {
		return Calendar.getInstance();
	}

}
