-- 멤버 일과 스터디별 풀이 집계 (docs/spec/data-model.md 4.3 `member_days`, `study_day_solves`, processes 5.5, S09).
-- 앱 테이블은 studybot 스키마에 둔다 (결정 제안서 004).

-- 멤버 일: 멤버 × 스터디 일. 진행 상황(푼 수, 면제권)과 판정 결과를 담고, 배정(S09)·면제권(S11)·판정(S12)이 잠그는 행이다 (5.5, 5.10).
-- 행은 INSERT ... ON CONFLICT DO NOTHING 다음 SELECT ... FOR UPDATE로 만들고 잠근다. 잠금 순서는 결정 제안서 007.
-- solved_count는 그 날의 study_day_solves 행 수다. 배정할 때 다시 센다 (S09 만들 것 4).
-- season_id는 그날을 담은 기수 행이다. 기수 밖인 날(1기 전 등)은 비어 있고, 그날은 판정하지 않는다 (R9, OD-07).
-- result는 판정 결과(glossary JudgmentResult)이고 비어 있으면 미판정이다. result·result_source·judged_at·rule_id는 판정(S12)이 채운다.
-- result_source는 값이 정해지지 않아 CHECK를 두지 않는다 (S12에서 정함, S09 PR 판단 기록).
-- rule_id는 판정에 쓴 규칙 버전이다. S09는 채우지 않는다. 1기 시작 전 규칙 변경은 첫 버전을 지우고 다시 만드니(S05)
-- 판정하지 않는 날의 행에 채우면 그 변경이 FK에 막힌다. 판정하는 날의 버전은 지워지지 않는다 (R2 소급 없음).
-- version은 JPA 낙관적 잠금 값이다. 네이티브 UPDATE도 1씩 올린다.
CREATE TABLE studybot.member_days (
	id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id          BIGINT      NOT NULL REFERENCES studybot.studies (id),
	user_id           BIGINT      NOT NULL REFERENCES studybot.users (id),
	study_day         DATE        NOT NULL,
	season_id         BIGINT      REFERENCES studybot.seasons (id),
	solved_count      INTEGER     NOT NULL DEFAULT 0,
	exemption_used_at TIMESTAMPTZ,
	result            TEXT,
	result_source     TEXT,
	judged_at         TIMESTAMPTZ,
	rule_id           BIGINT      REFERENCES studybot.study_rules (id),
	version           INTEGER     NOT NULL DEFAULT 0,
	CONSTRAINT member_days_study_user_day_uk UNIQUE (study_id, user_id, study_day),
	CONSTRAINT member_days_solved_count_ck CHECK (solved_count >= 0),
	CONSTRAINT member_days_result_ck CHECK (result IN ('SUCCESS', 'FAIL', 'EXEMPT_REST', 'EXEMPT_TICKET'))
);

-- 오늘 현황, 판정 배치 (data-model.md '인덱스 (먼저 만들 것)').
CREATE INDEX member_days_study_day_ix ON studybot.member_days (study_id, study_day);

-- 스터디별 풀이 집계: 풀이 기록 하나가 사용자가 속한 스터디마다 한 행씩 그 스터디의 스터디 일에 들어간다 (5.3 'push 처리 순서' 5).
-- 같은 문제는 같은 스터디 일에 한 번만 센다 (R5). 같은 문제를 두 번 커밋하거나 force push로 SHA가 바뀌어 풀이 기록이 둘이어도
-- 같은 날이면 이 UNIQUE에 걸려 한 행만 남는다 (T4, T5). 플랫폼·번호는 풀이 기록의 값을 그대로 옮긴다.
CREATE TABLE studybot.study_day_solves (
	id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	study_id        BIGINT NOT NULL REFERENCES studybot.studies (id),
	user_id         BIGINT NOT NULL REFERENCES studybot.users (id),
	study_day       DATE   NOT NULL,
	solve_record_id BIGINT NOT NULL REFERENCES studybot.solve_records (id),
	platform        TEXT   NOT NULL,
	problem_no      TEXT   NOT NULL,
	CONSTRAINT study_day_solves_problem_uk UNIQUE (study_id, user_id, study_day, platform, problem_no)
);

-- 현황 상세 (data-model.md '인덱스 (먼저 만들 것)'). 멤버 일의 푼 수를 다시 셀 때는 위 UNIQUE 인덱스의 앞부분을 쓴다.
CREATE INDEX study_day_solves_study_day_ix ON studybot.study_day_solves (study_id, study_day);
