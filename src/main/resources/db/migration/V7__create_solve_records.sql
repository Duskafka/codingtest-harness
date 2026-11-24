-- 풀이 기록 (docs/spec/data-model.md 4.3 `solve_records`, processes 5.3·5.4, S08).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).
--
-- 한 행이 '누가 어떤 문제를 언제 풀었다'는 기록 하나다. 스터디 일 배정(study_day_solves)은 S09가 이 행을 보고 한다.
-- source: GITHUB(push 웹훅, S08), SELF_REPORT(수동 인증, S11), RECOVERY(누락 복구, P2) (glossary.md '풀이 기록').
-- status: CONFIRMED(문제를 식별함), PENDING_IDENTIFICATION(확인 대기: 멤버가 문제를 지정해야 함, S10), IGNORED.
-- received_at은 인정 시각이다. push면 서버가 웹훅을 받은 시각(webhook_inbox.received_at)이지 처리한 시각이 아니다 (R3, D5).
--
-- 커밋 작성자 이름·이메일과 커밋 메시지는 남기지 않는다 (R16, security.md 9.8). 원본 경로(raw_path)만 남긴다 (5.4).
-- parser_version은 data-model 표에 없는 컬럼이다. 기록을 만든 파서 버전(ProblemParser.VERSION)이고, 파서를 고친 뒤
-- 확인 대기를 다시 분석할 대상을 고를 때 쓴다 (5.4 '만들 것', S08 만들 것 5, S08 PR 판단 기록).
-- repo_id는 GitHub의 레포 ID다 (linked_repositories.repo_id와 같은 값, 레포 이름은 바뀔 수 있음).
-- inbox_id는 이 기록을 만든 수신함 행이다. 수신함의 payload는 14일 뒤 비우지만 행은 남는다 (S15).
CREATE TABLE studybot.solve_records (
	id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	user_id        BIGINT      NOT NULL REFERENCES studybot.users (id),
	source         TEXT        NOT NULL,
	platform       TEXT,
	problem_no     TEXT,
	title          TEXT,
	received_at    TIMESTAMPTZ NOT NULL,
	commit_sha     TEXT,
	repo_id        BIGINT,
	inbox_id       BIGINT      REFERENCES studybot.webhook_inbox (id),
	raw_path       TEXT,
	parser_version INTEGER,
	status         TEXT        NOT NULL,
	CONSTRAINT solve_records_source_ck CHECK (source IN ('GITHUB', 'SELF_REPORT', 'RECOVERY')),
	CONSTRAINT solve_records_status_ck CHECK (status IN ('CONFIRMED', 'PENDING_IDENTIFICATION', 'IGNORED')),
	-- 문제 식별자 = (플랫폼, 번호) (R6). 둘은 함께 있거나 함께 비어 있다. 값은 verification.Platform과 ProblemRef 정규형이다.
	CONSTRAINT solve_records_problem_pair_ck CHECK ((platform IS NULL) = (problem_no IS NULL)),
	CONSTRAINT solve_records_platform_ck CHECK (platform IN ('BOJ', 'PROGRAMMERS', 'LEETCODE')),
	CONSTRAINT solve_records_problem_no_ck CHECK (problem_no ~ '^[1-9][0-9]*$'),
	-- 문제를 식별한 기록에는 문제가 있고, 확인 대기에는 없다. 확인 대기에 문제를 지정하면(S10) 문제를 채우고 CONFIRMED가 된다.
	CONSTRAINT solve_records_confirmed_problem_ck CHECK (status <> 'CONFIRMED' OR platform IS NOT NULL),
	CONSTRAINT solve_records_pending_problem_ck CHECK (status <> 'PENDING_IDENTIFICATION' OR platform IS NULL),
	-- push로 만든 기록은 어느 커밋·레포·수신함·파일·파서 버전에서 왔는지 늘 안다.
	CONSTRAINT solve_records_github_ck CHECK (source <> 'GITHUB' OR (commit_sha IS NOT NULL AND repo_id IS NOT NULL
		AND inbox_id IS NOT NULL AND raw_path IS NOT NULL AND parser_version IS NOT NULL)),
	CONSTRAINT solve_records_commit_sha_ck CHECK (commit_sha <> ''),
	CONSTRAINT solve_records_raw_path_ck CHECK (raw_path <> ''),
	CONSTRAINT solve_records_parser_version_ck CHECK (parser_version >= 1)
);

-- 같은 커밋의 같은 문제는 한 번만 (data-model.md 4.3). 같은 push를 다른 전달 ID로 다시 받아도 기록이 늘지 않는다 (S08).
-- force push로 같은 문제가 새 SHA로 오면 행이 하나 더 생기는 게 맞다. 같은 날 중복은 study_day_solves가 막는다 (R5).
CREATE UNIQUE INDEX solve_records_commit_problem_uk ON studybot.solve_records (user_id, commit_sha, platform, problem_no)
	WHERE commit_sha IS NOT NULL;
-- 같은 커밋의 같은 파일은 한 번만. data-model 표에 없는 제약이다 (S08 PR 스펙 변경 제안).
-- 확인 대기는 문제가 NULL이라 위 인덱스로는 다시 받은 push의 중복을 막지 못한다(NULL끼리는 같지 않음). 상태에 기대지 않는 키로 막아서,
-- 멤버가 문제를 지정해 CONFIRMED가 된 뒤(S10)에 같은 push를 다시 받아도 확인 대기가 다시 생기지 않게 한다.
-- 한 파일은 한 문제로만 식별되므로 문제를 식별한 기록끼리는 부딪히지 않는다.
CREATE UNIQUE INDEX solve_records_commit_path_uk ON studybot.solve_records (user_id, commit_sha, raw_path)
	WHERE commit_sha IS NOT NULL;

-- 내 풀이 (data-model.md '인덱스 (먼저 만들 것)', S10 GET /api/me/solves).
CREATE INDEX solve_records_user_received_idx ON studybot.solve_records (user_id, received_at);
