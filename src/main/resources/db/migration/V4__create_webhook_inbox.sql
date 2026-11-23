-- 웹훅 수신함 (docs/spec/data-model.md 4.3 `webhook_inbox`, processes 5.3, S06).
-- 서명을 확인한 웹훅을 처리하기 전에 먼저 한 행으로 남긴다. 같은 전달(source, delivery_id)은 한 번만 남는다 (T2).
-- P1은 받은 요청 안에서 바로 처리하고 status를 PROCESSED·IGNORED·FAILED로 바꾼다 (D21).
-- attempts·next_attempt_at·DEAD는 P2 워커(재시도)가 쓴다. P1은 처리할 때 attempts만 1 올린다.
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).
--
-- payload는 원본 JSON이다. 커밋 작성자 이메일이 들어 있어 14일 뒤 지운다 (R16, S15). 그래서 NULL을 허용한다.
-- 받을 때는 늘 채운다 (github.persistence.WebhookInboxRepository#insertIfAbsent).
-- JSONB라 키 순서·공백이 정규화된다. 서명은 받을 때 원본 바이트로 확인하므로 다시 확인할 일은 없다.
CREATE TABLE studybot.webhook_inbox (
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	source          TEXT        NOT NULL,
	delivery_id     TEXT        NOT NULL,
	event_type      TEXT        NOT NULL,
	payload         JSONB,
	received_at     TIMESTAMPTZ NOT NULL,
	status          TEXT        NOT NULL,
	attempts        INTEGER     NOT NULL DEFAULT 0,
	next_attempt_at TIMESTAMPTZ,
	last_error      TEXT,
	processed_at    TIMESTAMPTZ,
	CONSTRAINT webhook_inbox_source_delivery_uk UNIQUE (source, delivery_id),
	CONSTRAINT webhook_inbox_source_ck CHECK (source IN ('GITHUB', 'DISCORD', 'KAKAO')),
	CONSTRAINT webhook_inbox_delivery_id_ck CHECK (delivery_id <> ''),
	CONSTRAINT webhook_inbox_event_type_ck CHECK (event_type <> ''),
	CONSTRAINT webhook_inbox_status_ck CHECK (status IN ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED', 'DEAD')),
	CONSTRAINT webhook_inbox_attempts_ck CHECK (attempts >= 0)
);

-- 워커 조회용 (data-model.md '인덱스 (먼저 만들 것)'). P2 워커가 처리할 행을 상태와 다음 시도 시각으로 찾는다.
CREATE INDEX webhook_inbox_status_next_attempt_idx ON studybot.webhook_inbox (status, next_attempt_at);
