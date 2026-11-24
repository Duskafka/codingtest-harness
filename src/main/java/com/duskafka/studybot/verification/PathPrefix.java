package com.duskafka.studybot.verification;

import org.jspecify.annotations.Nullable;

/** 뼈대: S08 구현 전. */
final class PathPrefix {

	static PathPrefix of(@Nullable String prefix) {
		return new PathPrefix();
	}

	boolean contains(String rawPath) {
		return false;
	}

}
