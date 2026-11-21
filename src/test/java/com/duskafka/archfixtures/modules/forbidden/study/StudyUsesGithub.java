package com.duskafka.archfixtures.modules.forbidden.study;

import com.duskafka.archfixtures.modules.forbidden.github.GithubService;

/** study → github: 4.2에 없는 방향이라 걸려야 한다. */
public class StudyUsesGithub {

	private final GithubService githubService = new GithubService();

	public void run() {
		githubService.handle();
	}

}
