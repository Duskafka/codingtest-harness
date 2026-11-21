package com.duskafka.archfixtures.modules.internal.notification;

import com.duskafka.archfixtures.modules.internal.study.StudyFacade;
import com.duskafka.archfixtures.modules.internal.study.internal.StudyRecord;

/** notification → study는 4.2에 있지만, study의 내부 패키지(StudyRecord)에 접근해서 걸려야 한다. */
public class NotificationUsesStudyInternal {

	private final StudyFacade facade = new StudyFacade();

	public String describe(StudyRecord record) {
		return facade.name() + record.id();
	}

}
