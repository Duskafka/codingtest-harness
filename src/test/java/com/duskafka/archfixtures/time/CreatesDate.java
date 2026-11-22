package com.duskafka.archfixtures.time;

import java.util.Date;

/** new Date(): 현재 시각으로 만들어져서 걸려야 한다. */
public class CreatesDate {

	public Date date() {
		return new Date();
	}

}
