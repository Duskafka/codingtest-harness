package com.duskafka.studybot.github.web;

import com.duskafka.studybot.github.connection.ConnectionViews.InstallationView;
import com.duskafka.studybot.github.connection.ConnectionViews.LinkView;
import com.duskafka.studybot.github.connection.ConnectionViews.RepositoriesView;
import com.duskafka.studybot.github.connection.ConnectionViews.RepositoryChoice;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.jspecify.annotations.Nullable;
import org.springframework.data.web.PagedModel;

/** GitHub 연결 API의 요청·응답 모양 (S07, 프론트가 쓰는 계약이라 PR 판단 기록 '꼭 봐 주세요'). */
final class GithubConnectionResponses {

	/**
	 * 조직 레포 안내 (S07 '주의할 것', 5.2). 조직 레포는 조직 관리자 승인이 필요해서 개인 레포를 쓰도록 응답에 싣는다.
	 */
	static final String ORGANIZATION_NOTICE = "조직(Organization) 레포는 조직 관리자의 승인이 있어야 연결할 수 있어요. 개인 계정의 레포를 골라 주세요.";

	/** 연결 뒤 안내 (5.2 그림 '테스트 커밋 확인 안내'). */
	static final String TEST_COMMIT_GUIDE = "풀이 레포를 연결했어요. 이 레포에 테스트 커밋을 올려 자동 인증이 되는지 확인해 보세요.";

	private GithubConnectionResponses() {
	}

	/** {@code GET /api/github/install-url}. */
	record InstallUrlResponse(String url, String notice) {
	}

	/** {@code PUT /api/me/linked-repository} 본문. */
	record LinkRepositoryRequest(@NotNull @Positive Long repoId) {
	}

	/** 내 설치. {@code status}: ACTIVE, SUSPENDED. */
	record InstallationResponse(String accountLogin, String status) {

		static @Nullable InstallationResponse from(@Nullable InstallationView view) {
			return view == null ? null : new InstallationResponse(view.accountLogin(), view.status().name());
		}

	}

	/** 내 풀이 레포. {@code status}: ACTIVE, SUSPENDED. */
	record LinkedRepositoryResponse(long repoId, String fullName, String defaultBranch, String status) {

		static @Nullable LinkedRepositoryResponse from(@Nullable LinkView view) {
			return view == null ? null
					: new LinkedRepositoryResponse(view.repoId(), view.fullName(), view.defaultBranch(), view.status().name());
		}

	}

	/** 고를 수 있는 레포. */
	record RepositoryResponse(long repoId, String fullName, String defaultBranch,
			@JsonProperty("private") boolean isPrivate, boolean linked) {

		static RepositoryResponse from(RepositoryChoice choice) {
			return new RepositoryResponse(choice.repoId(), choice.fullName(), choice.defaultBranch(), choice.isPrivate(),
					choice.linked());
		}

	}

	/** {@code GET /api/me/github/repositories}. 설치가 없으면 {@code installation}이 {@code null}이다. */
	record RepositoriesResponse(@Nullable InstallationResponse installation,
			@Nullable LinkedRepositoryResponse linkedRepository, PagedModel<RepositoryResponse> repositories,
			String notice) {

		static RepositoriesResponse from(RepositoriesView view) {
			return new RepositoriesResponse(InstallationResponse.from(view.installation()),
					LinkedRepositoryResponse.from(view.linkedRepository()),
					new PagedModel<>(view.repositories().map(RepositoryResponse::from)), ORGANIZATION_NOTICE);
		}

	}

	/** {@code PUT /api/me/linked-repository} 응답. */
	record LinkResponse(long repoId, String fullName, String defaultBranch, String status, String guide) {

		static LinkResponse from(LinkView view) {
			return new LinkResponse(view.repoId(), view.fullName(), view.defaultBranch(), view.status().name(),
					TEST_COMMIT_GUIDE);
		}

	}

}
