-- 스터디, 규칙(버전), 기수, 멤버십, 초대 (docs/spec/data-model.md 4.3, processes 5.1, S05).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).
-- 범위 CHECK는 R1 표(domain-rules.md 3.2)를 그대로 옮겼다. API 검증과 같은 범위를 DB에서도 한 번 더 지킨다.
-- 상태·종류 값은 glossary.md 이름을 쓴다. glossary에 없는 이름(penalty_type, self_report_mode, studies.status)은 S05 PR 판단 기록에 있다.

-- 스터디. 시간대는 만들 때 정하고 바꾸지 않는다 (5.1 '주의할 것').
-- last_judged_day·last_reminded_day는 판정 배치(S12, 5.10)와 알림 생성(S13, 5.8)이 채우는 진행 지점이다. S05에서는 비워 둔다.
CREATE TABLE studybot.studies (
	id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	name              TEXT        NOT NULL,
	mode              TEXT        NOT NULL,
	owner_id          BIGINT      NOT NULL REFERENCES studybot.users (id),
	timezone          TEXT        NOT NULL,
	status            TEXT        NOT NULL,
	last_judged_day   DATE,
	last_reminded_day DATE,
	created_at        TIMESTAMPTZ NOT NULL,
	CONSTRAINT studies_name_length_ck CHECK (char_length(name) BETWEEN 1 AND 50),
	CONSTRAINT studies_mode_ck CHECK (mode IN ('GROUP', 'SOLO')),
	CONSTRAINT studies_status_ck CHECK (status IN ('ACTIVE'))
);

-- 규칙 버전 (R2). 버전마다 한 행이고, effective_from부터 적용한다. 첫 버전의 effective_from은 스터디 시작일이다 (5.1).
-- rest_days는 쉬는 요일을 java.time.DayOfWeek 이름으로, 월요일부터 쉼표로 이어 쓴다 (예: 'SATURDAY,SUNDAY'). 없으면 빈 문자열.
-- deposit_amount는 R1에 범위가 없어 S05 API로 받지 않는다 (S05 PR 열린 질문). 컬럼만 data-model대로 둔다.
CREATE TABLE studybot.study_rules (
	id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id                BIGINT      NOT NULL REFERENCES studybot.studies (id),
	effective_from          DATE        NOT NULL,
	daily_goal              INTEGER     NOT NULL,
	day_boundary            TIME        NOT NULL,
	grace_minutes           INTEGER     NOT NULL,
	rest_days               TEXT        NOT NULL,
	penalty_type            TEXT        NOT NULL,
	fine_amount             INTEGER     NOT NULL,
	deposit_amount          INTEGER,
	exemptions_per_season   INTEGER     NOT NULL,
	reminder_offset_minutes INTEGER     NOT NULL,
	self_report_mode        TEXT        NOT NULL,
	season_length_days      INTEGER     NOT NULL,
	created_at              TIMESTAMPTZ NOT NULL,
	CONSTRAINT study_rules_study_effective_from_uk UNIQUE (study_id, effective_from),
	CONSTRAINT study_rules_daily_goal_ck CHECK (daily_goal BETWEEN 1 AND 5),
	CONSTRAINT study_rules_day_boundary_ck CHECK (day_boundary BETWEEN TIME '00:00' AND TIME '06:00'),
	CONSTRAINT study_rules_grace_minutes_ck CHECK (grace_minutes BETWEEN 0 AND 120),
	CONSTRAINT study_rules_rest_days_ck CHECK (rest_days ~
		'^((MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY)(,(MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY))*)?$'),
	CONSTRAINT study_rules_penalty_type_ck CHECK (penalty_type IN ('NONE', 'FINE', 'DEPOSIT_REFUND')),
	CONSTRAINT study_rules_fine_amount_ck CHECK (fine_amount BETWEEN 0 AND 10000),
	CONSTRAINT study_rules_deposit_amount_ck CHECK (deposit_amount IS NULL OR deposit_amount >= 0),
	CONSTRAINT study_rules_exemptions_ck CHECK (exemptions_per_season BETWEEN 0 AND 10),
	CONSTRAINT study_rules_reminder_offset_ck CHECK (reminder_offset_minutes BETWEEN 30 AND 360),
	CONSTRAINT study_rules_self_report_mode_ck CHECK (self_report_mode IN ('IMMEDIATE', 'APPROVAL_REQUIRED')),
	CONSTRAINT study_rules_season_length_ck CHECK (season_length_days BETWEEN 7 AND 56)
);

-- 기수 (R13, 3.4 상태). S05는 1기만 SCHEDULED로 만든다. 다음 기수(OD-07)와 마지막 날 뒤의 상태 전환은 S12다.
-- 시작일의 SCHEDULED → ACTIVE 전환을 맡은 단계는 아직 없다 (S05 PR 열린 질문).
CREATE TABLE studybot.seasons (
	id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id   BIGINT      NOT NULL REFERENCES studybot.studies (id),
	seq        INTEGER     NOT NULL,
	start_day  DATE        NOT NULL,
	end_day    DATE        NOT NULL,
	status     TEXT        NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT seasons_study_seq_uk UNIQUE (study_id, seq),
	CONSTRAINT seasons_seq_ck CHECK (seq >= 1),
	CONSTRAINT seasons_days_ck CHECK (end_day >= start_day),
	CONSTRAINT seasons_status_ck CHECK (status IN ('SCHEDULED', 'ACTIVE', 'SETTLING', 'CLOSED'))
);

-- 멤버십 (R14). 탈퇴하면 left_at을 채우고 행은 남긴다. 다시 가입하면 새 행이 생긴다.
CREATE TABLE studybot.memberships (
	id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id    BIGINT      NOT NULL REFERENCES studybot.studies (id),
	user_id     BIGINT      NOT NULL REFERENCES studybot.users (id),
	role        TEXT        NOT NULL,
	joined_at   TIMESTAMPTZ NOT NULL,
	judged_from DATE        NOT NULL,
	left_at     TIMESTAMPTZ,
	CONSTRAINT memberships_role_ck CHECK (role IN ('OWNER', 'MEMBER')),
	CONSTRAINT memberships_left_after_joined_ck CHECK (left_at IS NULL OR left_at >= joined_at)
);

-- 탈퇴하지 않은 멤버십은 스터디·사용자마다 하나. 탈퇴한 행은 빠지므로 다시 가입할 수 있다 (data-model 4.3).
CREATE UNIQUE INDEX memberships_active_member_uk ON studybot.memberships (study_id, user_id) WHERE left_at IS NULL;
-- 탈퇴하지 않은 방장은 스터디마다 하나 (방장 위임이 반쯤 끝난 상태를 막는다. S05 PR 판단 기록).
CREATE UNIQUE INDEX memberships_active_owner_uk ON studybot.memberships (study_id) WHERE role = 'OWNER' AND left_at IS NULL;
-- 내 스터디 목록 (data-model 4.3 '인덱스').
CREATE INDEX memberships_user_id_ix ON studybot.memberships (user_id);

-- 초대 (5.1). 토큰 원문은 저장하지 않고 SHA-256 해시(32바이트)만 둔다.
-- used_count는 수락할 때 `used_count < max_uses` 조건부 UPDATE로만 늘린다 (5.1 '정원 동시성').
CREATE TABLE studybot.invitations (
	id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id   BIGINT      NOT NULL REFERENCES studybot.studies (id),
	token_hash BYTEA       NOT NULL,
	expires_at TIMESTAMPTZ NOT NULL,
	max_uses   INTEGER     NOT NULL,
	used_count INTEGER     NOT NULL DEFAULT 0,
	revoked_at TIMESTAMPTZ,
	created_by BIGINT      NOT NULL REFERENCES studybot.users (id),
	created_at TIMESTAMPTZ NOT NULL,
	CONSTRAINT invitations_token_hash_uk UNIQUE (token_hash),
	CONSTRAINT invitations_token_hash_length_ck CHECK (octet_length(token_hash) = 32),
	CONSTRAINT invitations_max_uses_ck CHECK (max_uses >= 1),
	CONSTRAINT invitations_used_count_ck CHECK (used_count BETWEEN 0 AND max_uses)
);

CREATE INDEX invitations_study_id_ix ON studybot.invitations (study_id);
