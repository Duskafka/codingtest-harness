package com.duskafka.archfixtures.time;

import java.time.chrono.JapaneseDate;

/** JapaneseDate.now(): java.time.chrono 날짜도 시스템 시계라 걸려야 한다. */
public class CallsChronoDateNow {

	public JapaneseDate today() {
		return JapaneseDate.now();
	}

}
