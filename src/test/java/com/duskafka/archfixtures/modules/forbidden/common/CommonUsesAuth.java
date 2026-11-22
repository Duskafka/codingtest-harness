package com.duskafka.archfixtures.modules.forbidden.common;

import com.duskafka.archfixtures.modules.forbidden.auth.AuthUser;

/** common → auth: common은 다른 모듈에 의존하지 않아야 해서 걸려야 한다. */
public class CommonUsesAuth {

	public long idOf(AuthUser user) {
		return user.id();
	}

}
