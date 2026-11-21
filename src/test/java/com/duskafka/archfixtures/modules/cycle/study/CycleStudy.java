package com.duskafka.archfixtures.modules.cycle.study;

import com.duskafka.archfixtures.modules.cycle.github.CycleGithub;

/** study ↔ github 순환: 순환 금지 규칙에 걸려야 한다. */
public class CycleStudy {

	public CycleGithub github() {
		return new CycleGithub();
	}

}
