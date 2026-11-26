-- 수동 인증 (docs/spec/data-model.md 4.3 `self_reports`, R7, processes 5.6, S11).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).
--
-- 한 행이 멤버 한 사람이 스터디 하나의 스터디 일 하루에 낸 수동 인증 하나다. 그 스터디의 그날에만 센다
-- (사람이 정함, 2026-11-26 S11 세션 "그 스터디의 고른 날만"). 세는 일은 풀이 기록(solve_records, source = SELF_REPORT)을
-- 만들어 study_day_solves에 넣는 것이다. solve_record_id가 그 풀이 기록이다. data-model ER(SELF_REPORTS |o--o| SOLVE_RECORDS)대로
-- 비어 있을 수 있다. '바로 인정'은 늘 채운다. '승인 필요' 모드는 승인할 때 만들 수 있게 열어 둔다 (R7, 알려진 충돌 6번).
-- status: ACCEPTED(바로 인정, 센 상태), PENDING_APPROVAL('승인 필요' 모드, 아직 만들지 않음, 알려진 충돌 6번),
-- WITHDRAWN(본인 철회, 판정 전), REJECTED(방장 거부·반대 이의 인정, P2) (glossary SelfReport, 3.4).
-- decided_by·decided_at은 상태를 바꾼 사람과 시각이다. 본인 철회면 본인이다. '승인 필요' 모드의 방장 승인(PENDING_APPROVAL → ACCEPTED)도
-- 남길 수 있게 상태와 묶는 CHECK는 두지 않는다 (S11 spec-reviewer).
-- 하루 5건(R7)은 (study_id, user_id, study_day)의 행 수로 센다. 철회한 것도 받은 것으로 센다 (S11 PR 판단 기록).
-- 증거 링크는 http·https만 받는다 (security.md 9.7). 서버는 이 링크에 접속하지 않는다.
CREATE TABLE studybot.self_reports (
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id        BIGINT      NOT NULL REFERENCES studybot.studies (id),
	user_id         BIGINT      NOT NULL REFERENCES studybot.users (id),
	study_day       DATE        NOT NULL,
	platform        TEXT        NOT NULL,
	problem_no      TEXT        NOT NULL,
	evidence_url    TEXT,
	memo            TEXT,
	status          TEXT        NOT NULL,
	solve_record_id BIGINT      REFERENCES studybot.solve_records (id),
	decided_by      BIGINT      REFERENCES studybot.users (id),
	decided_at      TIMESTAMPTZ,
	created_at      TIMESTAMPTZ NOT NULL,
	CONSTRAINT self_reports_status_ck CHECK (status IN ('ACCEPTED', 'PENDING_APPROVAL', 'WITHDRAWN', 'REJECTED')),
	-- 문제 식별자 = (플랫폼, 번호) (R6). 값은 verification.Platform과 ProblemRef 정규형이다 (solve_records와 같음).
	CONSTRAINT self_reports_platform_ck CHECK (platform IN ('BOJ', 'PROGRAMMERS', 'LEETCODE')),
	CONSTRAINT self_reports_problem_no_ck CHECK (problem_no ~ '^[1-9][0-9]*$'),
	CONSTRAINT self_reports_evidence_url_ck CHECK (evidence_url ~* '^https?://[^[:space:]]+$'),
	CONSTRAINT self_reports_memo_ck CHECK (char_length(memo) <= 500),
	-- 풀이 기록 하나는 수동 인증 하나에서만 나온다.
	CONSTRAINT self_reports_solve_record_uk UNIQUE (solve_record_id),
	-- 결정한 사람과 시각은 함께 있다.
	CONSTRAINT self_reports_decided_ck CHECK ((decided_by IS NULL) = (decided_at IS NULL))
);

-- 하루 5건 세기, 그날의 수동 인증 (R7).
CREATE INDEX self_reports_study_user_day_ix ON studybot.self_reports (study_id, user_id, study_day);
