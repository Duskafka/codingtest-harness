-- 판정 배치의 중복 실행 방지와 기수 정산 시각 (docs/spec/data-model.md 4.3 `shedlock`, `seasons`, processes 5.10·5.13, S12).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).

-- ShedLock JDBC 잠금 테이블 (S12 만들 것 1). 이름·타입은 ShedLock README의 PostgreSQL 예시 그대로다.
-- 잠금 시각은 DB 시각으로 센다(JdbcTemplateLockProvider usingDbTime). 서버 시계가 서로 달라도 같은 기준이다.
CREATE TABLE studybot.shedlock (
	name       VARCHAR(64)  NOT NULL,
	lock_until TIMESTAMP    NOT NULL,
	locked_at  TIMESTAMP    NOT NULL,
	locked_by  VARCHAR(255) NOT NULL,
	PRIMARY KEY (name)
);

-- 기수가 정산 중이 된 시각 (R13). 마지막 날 판정이 끝나 last_judged_day가 끝 날짜에 닿은 시각이다.
-- 이 시각 + 72시간(이의 기간 3일)이 지나면 배치가 종료(CLOSED)로 바꾼다 (사람이 정함, 2026-11-27 "실제 판정 시각 + 72시간").
-- 정산 중·종료일 때만, 그리고 늘 있다. 그 전 상태의 행(지금 있는 행은 모두 SCHEDULED)은 비어 있다.
ALTER TABLE studybot.seasons ADD COLUMN settling_started_at TIMESTAMPTZ;
ALTER TABLE studybot.seasons ADD CONSTRAINT seasons_settling_started_at_ck
	CHECK ((status IN ('SETTLING', 'CLOSED')) = (settling_started_at IS NOT NULL));
