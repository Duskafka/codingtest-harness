-- 사용자 (docs/spec/data-model.md 4.3 `users`).
-- 로그인할 때 GitHub /user 응답에서 github_id·github_login·avatar_url만 받아 저장하거나 갱신한다 (api.md 4.5, S04).
-- 이메일은 받지 않는다 (security.md 9.8 '최소 수집'). 탈퇴 시 익명화는 S15 (5.16).
-- 5.16은 탈퇴하면 github_id를 지운다. 그래서 github_id는 NULL을 허용하되, 탈퇴하지 않은 사용자는 반드시 갖게 한다
-- (users_github_id_present_ck). UNIQUE는 NULL끼리는 겹치지 않는다.
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004). 스키마는 Flyway가 만든다 (spring.flyway.schemas).

CREATE TABLE studybot.users (
	id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	github_id    BIGINT,
	github_login TEXT        NOT NULL,
	avatar_url   TEXT,
	created_at   TIMESTAMPTZ NOT NULL,
	deleted_at   TIMESTAMPTZ,
	CONSTRAINT users_github_id_uk UNIQUE (github_id),
	CONSTRAINT users_github_id_present_ck CHECK (github_id IS NOT NULL OR deleted_at IS NOT NULL)
);
