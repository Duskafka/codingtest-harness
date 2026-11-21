package com.duskafka.archfixtures.modules.allowed.study;

import com.duskafka.archfixtures.modules.allowed.study.internal.StudyInternal;

/** 같은 모듈 안에서는 내부 패키지를 써도 된다. */
public class StudyService {

	private final StudyInternal internal = new StudyInternal();

	public String name() {
		return internal.name();
	}

}
