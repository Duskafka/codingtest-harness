package com.duskafka.studybot.github.persistence;

/** 풀이 레포 연결의 상태 ({@code linked_repositories.status}). */
public enum LinkedRepositoryStatus {

	/** 연결됨. 이 레포의 push를 풀이로 받는다 (S08). */
	ACTIVE,

	/** 설치가 정지된 동안 꺼짐. unsuspend면 ACTIVE로 돌아간다. */
	SUSPENDED,

	/** 끊김: 레포가 설치에서 빠졌거나, 설치가 삭제됐거나, 사용자가 다른 레포를 골랐다. 되살리지 않는다. */
	INACTIVE

}
