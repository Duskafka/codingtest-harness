-- GitHub App 설치와 풀이 레포 연결 (docs/spec/data-model.md 4.3 `github_installations`, `linked_repositories`, processes 5.2, S07).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).

-- 설치 하나가 한 행이다. installation_id는 GitHub가 정하고, 삭제 후 다시 설치하면 새 값이 된다.
-- user_id는 서명된 installation 웹훅의 설치자 정보로만 채운다 (5.2, T21). 개인 계정(account_type = 'User')이고
-- 계정 주인이 직접 설치했을 때(installed_by_owner, 웹훅의 account.id = sender.id)만 그 GitHub ID의 사용자에게 잇는다.
-- 아직 가입하지 않았으면 NULL로 두고 가입할 때 잇는다.
-- account_type·installed_by_owner는 data-model 표에 없는 컬럼이다. 가입할 때 위 조건을 다시 확인하려고 둔다 (S07 PR 판단 기록).
-- 설치자(sender)의 GitHub ID는 남기지 않는다. 조직 설치나 남이 대신 설치하면 회원이 아닌 사람의 ID라서다 (security.md 9.8 최소 수집).
-- 행을 고친 시각 updated_at도 표에 없는 컬럼이다 (상태가 언제 바뀌었는지 보려고).
-- status: ACTIVE, SUSPENDED(installation suspend), DELETED(installation deleted). 삭제해도 행은 남긴다 (계정 삭제 때 지우는 건 S15).
-- 웹훅 전달 순서는 보장되지 않아서, 처음 보는 설치의 deleted·suspend·unsuspend도 그 상태로 행을 남긴다(늦게 온 created가 되살리지 않게).
-- 이때는 설치 ID와 상태만 남기고 계정 값(account_id, account_login, account_type)과 installed_by_owner는 NULL로 둔다.
-- 그 이벤트의 sender는 정지·삭제한 사람이지 설치한 사람이 아니고, installation.account는 null일 수도 있어서다.
-- 계정 값과 설치자 여부는 created가 채운다 (5.2 처리 순서 1, PR #14 2차 리뷰 반영). 채울 때 status는 바꾸지 않는다.
-- 삭제된 설치(DELETED)에는 채우지 않는다. 다시 쓰이지 않는 설치라 비회원일 수 있는 계정 값을 남길 이유가 없다 (security.md 9.8).
-- NULL인 행은 claim의 조건(installed_by_owner)을 넘지 못해 사용자에게 이어지지 않는다.
-- repositories_revision은 표에 없는 컬럼이다. installation_repositories removed마다 1 오른다. GitHub에서 레포 목록을 읽은 뒤
-- 연결을 저장할 때 그사이 빠진 레포가 없는지 이 값으로 본다 (PR #14 리뷰 반영).
CREATE TABLE studybot.github_installations (
	installation_id BIGINT      PRIMARY KEY,
	user_id         BIGINT      REFERENCES studybot.users (id),
	account_id      BIGINT,
	account_login   TEXT,
	account_type    TEXT,
	installed_by_owner BOOLEAN,
	status          TEXT        NOT NULL,
	repositories_revision BIGINT NOT NULL DEFAULT 0,
	created_at      TIMESTAMPTZ NOT NULL,
	updated_at      TIMESTAMPTZ NOT NULL,
	CONSTRAINT github_installations_status_ck CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
	CONSTRAINT github_installations_account_type_ck CHECK (account_type <> ''),
	-- 계정 값과 설치자 여부는 created가 한꺼번에 채운다: 모두 있거나 모두 NULL이다.
	CONSTRAINT github_installations_account_ck CHECK (
		(account_id IS NULL AND account_login IS NULL AND account_type IS NULL AND installed_by_owner IS NULL)
		OR (account_id IS NOT NULL AND account_login IS NOT NULL AND account_type IS NOT NULL
			AND installed_by_owner IS NOT NULL)),
	-- 사용자에게 이은 설치에는 계정 값이 있다 (claim은 계정 주인이 설치한 행만 잇는다).
	CONSTRAINT github_installations_linked_account_ck CHECK (user_id IS NULL OR account_id IS NOT NULL)
);

-- 내 설치 찾기, 가입할 때 내 GitHub ID의 설치 찾기
CREATE INDEX github_installations_user_idx ON studybot.github_installations (user_id);
CREATE INDEX github_installations_account_idx ON studybot.github_installations (account_id);

-- 사용자가 고른 풀이 레포. 이름이 아니라 repo_id로 추적한다 (레포 이름은 바뀔 수 있다).
-- 연결을 바꾸거나 끊어도 행은 지우지 않고 status만 바꾼다 (push가 어느 연결로 인정됐는지 남기기 위해).
-- status: ACTIVE, SUSPENDED(설치 정지 동안. unsuspend면 ACTIVE로 돌아간다), INACTIVE(레포가 설치에서 빠짐·설치 삭제·다른 레포로 바꿈. 되살리지 않는다).
-- path_prefix는 S08(확인 대기 판단)에서 쓴다. 지금은 비워 둔다. created_at·updated_at은 data-model 표에 없는 컬럼이다.
CREATE TABLE studybot.linked_repositories (
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	user_id         BIGINT      NOT NULL REFERENCES studybot.users (id),
	installation_id BIGINT      NOT NULL REFERENCES studybot.github_installations (installation_id),
	repo_id         BIGINT      NOT NULL,
	full_name       TEXT        NOT NULL,
	default_branch  TEXT        NOT NULL,
	path_prefix     TEXT,
	status          TEXT        NOT NULL,
	created_at      TIMESTAMPTZ NOT NULL,
	updated_at      TIMESTAMPTZ NOT NULL,
	CONSTRAINT linked_repositories_status_ck CHECK (status IN ('ACTIVE', 'SUSPENDED', 'INACTIVE')),
	CONSTRAINT linked_repositories_full_name_ck CHECK (full_name <> ''),
	CONSTRAINT linked_repositories_default_branch_ck CHECK (default_branch <> '')
);

-- 한 레포는 한 사람에게만 활성으로 연결된다 (data-model.md 4.3). push를 받으면 이 인덱스로 연결을 찾는다 (S08).
CREATE UNIQUE INDEX linked_repositories_active_repo_uk ON studybot.linked_repositories (repo_id) WHERE status = 'ACTIVE';
-- MVP는 사용자당 레포 1개다 (5.2 '주의할 것'). 정지된 연결도 되살아날 수 있어 함께 센다 (S07 PR 판단 기록).
CREATE UNIQUE INDEX linked_repositories_current_user_uk ON studybot.linked_repositories (user_id)
	WHERE status IN ('ACTIVE', 'SUSPENDED');
-- 설치 이벤트(정지·삭제·레포 제거)가 그 설치의 연결을 찾는다.
CREATE INDEX linked_repositories_installation_idx ON studybot.linked_repositories (installation_id);
