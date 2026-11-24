package com.duskafka.studybot.github.api;

import java.util.List;

/**
 * 설치 레포 목록의 한 페이지.
 *
 * @param totalCount 설치에서 볼 수 있는 레포 전체 수
 * @param repositories 이 페이지의 레포
 */
public record RepositoryPage(int totalCount, List<InstalledRepository> repositories) {

	public RepositoryPage {
		repositories = List.copyOf(repositories);
	}

}
