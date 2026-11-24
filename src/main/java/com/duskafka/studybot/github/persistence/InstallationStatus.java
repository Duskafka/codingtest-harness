package com.duskafka.studybot.github.persistence;

/** GitHub App 설치의 상태 ({@code github_installations.status}). */
public enum InstallationStatus {

	/** 쓰는 중. */
	ACTIVE,

	/** 정지됨 ({@code installation} suspend). unsuspend면 ACTIVE로 돌아간다. */
	SUSPENDED,

	/** 삭제됨 ({@code installation} deleted). 되살아나지 않는다. 다시 설치하면 새 설치 ID가 된다. */
	DELETED

}
