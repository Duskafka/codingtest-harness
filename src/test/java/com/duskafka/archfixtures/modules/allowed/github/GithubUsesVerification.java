package com.duskafka.archfixtures.modules.allowed.github;

import com.duskafka.archfixtures.modules.allowed.verification.VerificationService;

/** github → verification: 4.2에 있는 방향. */
public class GithubUsesVerification {

	private final VerificationService verificationService = new VerificationService();

	public void run() {
		verificationService.record();
	}

}
