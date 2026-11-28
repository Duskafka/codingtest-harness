-- 디스코드 채널 설정과 디스코드·결과 알림 (docs/spec/data-model.md 4.3 `study_channels`·`notifications`, processes 5.8·5.9, S14).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).

-- 스터디의 디스코드 채널 웹훅. 스터디마다 한 행이다 (등록할 때 만든다).
-- 웹훅 URL은 앱 수준에서 암호화해 저장한다 (9.6 '디스코드 웹훅 URL 암호화 키', S14 PR 판단 기록 '암호화 방식과 키 교체 절차').
-- 형식은 v1:{키 ID}:{base64url(nonce|암호문|태그)}이고, 평문 URL은 어디에도 남기지 않는다. 등록한 뒤에는 API로도 다시 보여 주지 않는다.
-- discord_revision은 등록·해제할 때마다 1씩 오른다. 404를 받아 끌 때 그사이 방장이 다시 등록한 새 웹훅을 끄지 않게 한다 (S14 PR 판단 기록).
-- discord_last_error는 마지막 실패의 까닭이다(예: HTTP_404). 응답 본문은 남기지 않는다.
CREATE TABLE studybot.study_channels (
	study_id                BIGINT      PRIMARY KEY REFERENCES studybot.studies (id),
	discord_webhook_url_enc TEXT,
	discord_enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
	discord_last_error      TEXT,
	discord_revision        BIGINT      NOT NULL DEFAULT 0,
	updated_at              TIMESTAMPTZ NOT NULL,
	-- 켜져 있으면 주소가 있다. 꺼진 채 주소가 남는 것은 404·키 문제로 꺼진 경우다(다시 등록하면 바뀜).
	CONSTRAINT study_channels_enabled_url_ck CHECK (NOT discord_enabled OR discord_webhook_url_enc IS NOT NULL),
	CONSTRAINT study_channels_url_enc_length_ck CHECK (discord_webhook_url_enc IS NULL
		OR char_length(discord_webhook_url_enc) BETWEEN 1 AND 1024),
	CONSTRAINT study_channels_last_error_length_ck CHECK (discord_last_error IS NULL OR char_length(discord_last_error) <= 100),
	CONSTRAINT study_channels_revision_ck CHECK (discord_revision >= 0)
);

-- 알림 종류: S14가 디스코드 현황(DISCORD_STATUS), 결과 알림(DAILY_RESULT), 웹훅이 지워져 끈 것을 방장에게 알리는 DISCORD_DISABLED를 더한다.
-- DISCORD_DISABLED는 5.8 표에 없는 종류다 (S14 만들 것 4 '방장에게 알린다', S14 PR 스펙 변경 제안).
ALTER TABLE studybot.notifications DROP CONSTRAINT notifications_type_ck;
ALTER TABLE studybot.notifications ADD CONSTRAINT notifications_type_ck
	CHECK (type IN ('REMINDER', 'SHARE_PROMPT', 'RULE_CHANGED', 'DISCORD_STATUS', 'DAILY_RESULT', 'DISCORD_DISABLED'));

-- 채널: 디스코드 스터디 채널을 더한다. 디스코드 행은 사람이 아니라 스터디 채널로 가서 받는 사람이 없다.
ALTER TABLE studybot.notifications DROP CONSTRAINT notifications_channel_ck;
ALTER TABLE studybot.notifications ADD CONSTRAINT notifications_channel_ck CHECK (channel IN ('WEB_PUSH', 'DISCORD'));
ALTER TABLE studybot.notifications ADD CONSTRAINT notifications_discord_no_user_ck
	CHECK (channel <> 'DISCORD' OR user_id IS NULL);
