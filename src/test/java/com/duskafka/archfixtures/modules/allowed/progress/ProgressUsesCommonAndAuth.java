package com.duskafka.archfixtures.modules.allowed.progress;

import com.duskafka.archfixtures.modules.allowed.auth.CurrentUser;
import com.duskafka.archfixtures.modules.allowed.common.time.CommonHelper;

/** 모든 모듈은 common(하위 패키지 포함)과 auth의 공개 API를 쓸 수 있다. */
public class ProgressUsesCommonAndAuth {

	public long run(CurrentUser user) {
		return user.id() + CommonHelper.one();
	}

}
