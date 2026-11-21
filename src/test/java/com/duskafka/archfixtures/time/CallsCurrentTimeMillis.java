package com.duskafka.archfixtures.time;

/** System.currentTimeMillis(): 걸려야 한다. */
public class CallsCurrentTimeMillis {

	public long millis() {
		return System.currentTimeMillis();
	}

}
