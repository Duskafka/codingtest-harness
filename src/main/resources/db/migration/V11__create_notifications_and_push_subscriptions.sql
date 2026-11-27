-- 알림 outbox와 웹 푸시 구독 (docs/spec/data-model.md 4.3 `notifications`, `push_subscriptions`, processes 5.8, S13).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).

-- 알림 outbox. 생성(스케줄러·규칙 변경)과 발송(발송기)을 나눈다 (5.8 '만들 것').
-- dedupe_key UNIQUE로 같은 알림을 두 번 만들지 않는다 (5.8, T15). 키 형식은 5.8 '알림 종류' 표다.
-- 종류는 P1 다섯 가지만 받는다. P2·P3 종류(WEEKLY_SETTLEMENT, APPEAL, SEASON_SUMMARY)는 그 단계에서 마이그레이션으로 더한다.
-- created_at은 data-model 표에 없지만 지표(알림 전환율)·장애 분석에 필요해 더했다 (S13 PR 판단 기록).
-- payload는 웹 푸시로 보낼 JSON 그대로다. 개인정보는 넣지 않는다 (5.8 '주의할 것').
CREATE TABLE studybot.notifications (
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	type            TEXT        NOT NULL,
	channel         TEXT        NOT NULL,
	study_id        BIGINT      NOT NULL REFERENCES studybot.studies (id),
	user_id         BIGINT REFERENCES studybot.users (id),
	study_day       DATE,
	dedupe_key      TEXT        NOT NULL,
	payload         JSONB       NOT NULL,
	status          TEXT        NOT NULL,
	attempts        INTEGER     NOT NULL DEFAULT 0,
	next_attempt_at TIMESTAMPTZ NOT NULL,
	locked_until    TIMESTAMPTZ,
	sent_at         TIMESTAMPTZ,
	expires_at      TIMESTAMPTZ NOT NULL,
	last_error      TEXT,
	created_at      TIMESTAMPTZ NOT NULL,
	CONSTRAINT notifications_dedupe_key_uk UNIQUE (dedupe_key),
	CONSTRAINT notifications_type_ck CHECK (type IN ('REMINDER', 'SHARE_PROMPT', 'DISCORD_STATUS', 'DAILY_RESULT', 'RULE_CHANGED')),
	CONSTRAINT notifications_channel_ck CHECK (channel IN ('WEB_PUSH', 'DISCORD')),
	CONSTRAINT notifications_status_ck CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'SKIPPED', 'EXPIRED')),
	CONSTRAINT notifications_attempts_ck CHECK (attempts >= 0),
	-- 발송 중(SENDING)일 때만, 그리고 늘 잠금 만료 시각이 있다. 이 값이 발송기 한 번의 소유 표시다.
	CONSTRAINT notifications_locked_until_ck CHECK ((status = 'SENDING') = (locked_until IS NOT NULL)),
	-- 보낸(SENT) 알림만, 그리고 늘 보낸 시각이 있다.
	CONSTRAINT notifications_sent_at_ck CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
	-- 웹 푸시는 사람에게 보낸다. 디스코드(S14)는 스터디 채널이라 사람이 없을 수 있다.
	CONSTRAINT notifications_web_push_user_ck CHECK (channel <> 'WEB_PUSH' OR user_id IS NOT NULL)
);

-- 발송기가 보낼 행을 찾는다 (data-model 4.3 '인덱스').
CREATE INDEX notifications_status_next_attempt_ix ON studybot.notifications (status, next_attempt_at);

-- 웹 푸시 구독. 기기(브라우저)마다 한 행이다. endpoint는 푸시 서비스가 준 주소이고 기기를 가리키는 비밀 주소다.
-- 등록할 때 알려진 푸시 서비스 주소만 받는다 (security.md 9.7, T28). 404·410을 받으면 지운다 (5.8, T17).
-- p256dh·auth는 브라우저가 준 base64url 문자열 그대로다 (RFC 8291).
CREATE TABLE studybot.push_subscriptions (
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	user_id         BIGINT      NOT NULL REFERENCES studybot.users (id),
	endpoint        TEXT        NOT NULL,
	p256dh          TEXT        NOT NULL,
	auth            TEXT        NOT NULL,
	user_agent      TEXT,
	created_at      TIMESTAMPTZ NOT NULL,
	last_success_at TIMESTAMPTZ,
	failure_count   INTEGER     NOT NULL DEFAULT 0,
	CONSTRAINT push_subscriptions_endpoint_uk UNIQUE (endpoint),
	CONSTRAINT push_subscriptions_endpoint_length_ck CHECK (char_length(endpoint) BETWEEN 1 AND 2048),
	CONSTRAINT push_subscriptions_user_agent_length_ck CHECK (user_agent IS NULL OR char_length(user_agent) <= 512),
	CONSTRAINT push_subscriptions_failure_count_ck CHECK (failure_count >= 0)
);

-- 사용자의 구독으로 보낸다.
CREATE INDEX push_subscriptions_user_id_ix ON studybot.push_subscriptions (user_id);
