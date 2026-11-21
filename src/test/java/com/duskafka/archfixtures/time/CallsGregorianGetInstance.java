package com.duskafka.archfixtures.time;

import java.util.Calendar;
import java.util.GregorianCalendar;

/** GregorianCalendar.getInstance(): Calendar의 정적 메서드를 하위 클래스 이름으로 불러도 걸려야 한다. */
public class CallsGregorianGetInstance {

	public Calendar calendar() {
		return GregorianCalendar.getInstance();
	}

}
