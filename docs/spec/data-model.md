# 데이터 모델

> 스펙 문서예요. 에이전트는 고치지 않아요.
>
> - 테이블은 그 테이블이 필요한 단계에서 Flyway 마이그레이션으로 만들어요. 한꺼번에 미리 만들지 않아요.
> - 첫 마이그레이션 전에 스키마 이름(OD-06)이 정해져야함.
> - 표의 컬럼은 출발점이에요. 단계에서 컬럼을 더해야 하면 판단 기록에 남기고, 의미가 바뀌는 변경(제약, 상태 값, 컬럼 삭제)은 스펙 변경 제안으로 올려요.
> - 상태 값과 enum 이름은 `docs/spec/glossary.md`를 따라요.

## 4.3 데이터 모델 (PostgreSQL)

```mermaid
erDiagram
    USERS ||--o{ MEMBERSHIPS: "가입"
    STUDIES ||--o{ MEMBERSHIPS: "멤버"
    STUDIES ||--o{ STUDY_RULES: "규칙 버전"
    STUDIES ||--o{ SEASONS: "기수"
    STUDIES ||--o{ INVITATIONS: "초대"
    STUDIES ||--o| STUDY_CHANNELS: "채널 설정"
    USERS ||--o{ GITHUB_INSTALLATIONS: "설치"
    GITHUB_INSTALLATIONS ||--o{ LINKED_REPOSITORIES: "레포"
    USERS ||--o{ SOLVE_RECORDS: "풀이"
    SOLVE_RECORDS ||--o{ STUDY_DAY_SOLVES: "스터디별 집계"
    STUDIES ||--o{ MEMBER_DAYS: "멤버 일"
    USERS ||--o{ MEMBER_DAYS: "멤버 일"
    STUDIES ||--o{ SELF_REPORTS: "수동 인증"
    SELF_REPORTS |o--o| SOLVE_RECORDS: "풀이 기록"
    MEMBER_DAYS ||--o{ APPEALS: "이의 P2"
    MEMBER_DAYS ||--o{ PENALTY_LEDGER: "원장 P2"
    USERS ||--o{ PUSH_SUBSCRIPTIONS: "푸시 구독"
    STUDIES ||--o{ NOTIFICATIONS: "알림"

    USERS {
        bigint id PK
        bigint github_id UK
        string github_login
        timestamptz deleted_at
    }
    STUDIES {
        bigint id PK
        string mode "GROUP, SOLO"
        bigint owner_id FK
        string timezone
        date last_judged_day
    }
    MEMBER_DAYS {
        bigint id PK
        bigint study_id FK
        bigint user_id FK
        date study_day
        int solved_count
        timestamptz exemption_used_at
        string result "NULL이면 미판정"
        int version
    }
    SOLVE_RECORDS {
        bigint id PK
        bigint user_id FK
        string source "GITHUB, SELF_REPORT, RECOVERY"
        string platform
        string problem_no
        timestamptz received_at
        string commit_sha
        string status
    }
    STUDY_DAY_SOLVES {
        bigint study_id FK
        bigint user_id FK
        date study_day
        string platform
        string problem_no
    }
```

**테이블 상세**

| 테이블                                        | 단계 | 주요 컬럼                                                                                                                                                                                                         | 제약·비고                                                                      |
|-----------------------------------------------|------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `users`                                       | P1   | id, github_id, github_login, avatar_url, created_at, deleted_at                                                                                                                                                   | github_id UNIQUE. 탈퇴 시 익명화 (5.16)                                        |
| `studies`                                     | P1   | id, name, mode(GROUP/SOLO), owner_id, timezone, status, last_judged_day, last_reminded_day, created_at                                                                                                            | 판정 배치(5.10)와 알림 생성(5.8)의 진행 지점                                   |
| `study_rules`                                 | P1   | id, study_id, effective_from, daily_goal, day_boundary, grace_minutes, rest_days, penalty_type, fine_amount, deposit_amount, exemptions_per_season, reminder_offset_minutes, self_report_mode, season_length_days | (study_id, effective_from) UNIQUE. 규칙 버전 (R2)                              |
| `seasons`                                     | P1   | id, study_id, seq, start_day, end_day, status                                                                                                                                                                     | (study_id, seq) UNIQUE                                                         |
| `memberships`                                 | P1   | id, study_id, user_id, role(OWNER/MEMBER), joined_at, judged_from, left_at                                                                                                                                        | `(study_id, user_id) WHERE left_at IS NULL` 부분 UNIQUE → 탈퇴 후 재가입 가능  |
| `invitations`                                 | P1   | id, study_id, token_hash, expires_at, max_uses, used_count, revoked_at, created_by                                                                                                                                | token_hash UNIQUE. 토큰 원문은 저장하지 않음                                   |
| `github_installations`                        | P1   | installation_id, user_id, account_id, account_login, status, created_at                                                                                                                                           | installation_id UNIQUE. user_id는 서명된 웹훅의 설치자로 연결 (5.2)            |
| `linked_repositories`                         | P1   | id, user_id, installation_id, repo_id, full_name, default_branch, path_prefix, status                                                                                                                             | `repo_id WHERE status='ACTIVE'` 부분 UNIQUE. 이름이 아니라 repo_id로 추적      |
| `webhook_inbox`                               | P1   | id, source(GITHUB/DISCORD/KAKAO), delivery_id, event_type, payload(JSONB), received_at, status, attempts, next_attempt_at, last_error, processed_at                                                               | **(source, delivery_id) UNIQUE**. payload는 14일 뒤 삭제                       |
| `solve_records`                               | P1   | id, user_id, source, platform, problem_no, title, received_at, commit_sha, repo_id, inbox_id, raw_path, status(CONFIRMED/PENDING_IDENTIFICATION/IGNORED)                                                          | `(user_id, commit_sha, platform, problem_no)` UNIQUE (commit_sha 있을 때)      |
| `study_day_solves`                            | P1   | id, study_id, user_id, study_day, solve_record_id, platform, problem_no                                                                                                                                           | **(study_id, user_id, study_day, platform, problem_no) UNIQUE** → R5           |
| `member_days`                                 | P1   | id, study_id, user_id, study_day, season_id, solved_count, exemption_used_at, result, result_source, judged_at, rule_id, version                                                                                  | **(study_id, user_id, study_day) UNIQUE**. 잠금 대상이자 판정 결과 (5.5, 5.10) |
| `self_reports`                                | P1   | id, study_id, user_id, study_day, platform, problem_no, evidence_url, memo, status, solve_record_id, decided_by, decided_at, created_at                                                                           | 하루 5건 제한 (R7)                                                             |
| `notifications`                               | P1   | id, type, channel, study_id, user_id, study_day, dedupe_key, payload(JSONB), status, attempts, next_attempt_at, locked_until, sent_at, expires_at, last_error                                                     | **dedupe_key UNIQUE** → 같은 알림 중복 생성 방지 (5.8)                         |
| `push_subscriptions`                          | P1   | id, user_id, endpoint, p256dh, auth, user_agent, created_at, last_success_at, failure_count                                                                                                                       | endpoint UNIQUE                                                                |
| `study_channels`                              | P1   | study_id, discord_webhook_url_enc, discord_enabled, discord_last_error                                                                                                                                            | 웹훅 URL은 앱 수준에서 암호화해 저장 (9.6)                                     |
| `spring_session`, `spring_session_attributes` | P1   | Spring Session JDBC 기본 스키마                                                                                                                                                                                   | D22                                                                            |
| `shedlock`                                    | P1   | name, lock_until, locked_at, locked_by                                                                                                                                                                            | 스케줄러 중복 실행 방지                                                        |
| `appeals`                                     | P2   | id, study_id, type(JUDGMENT/SELF_REPORT_OBJECTION), member_day_id, self_report_id, requested_by, reason, evidence_url, status, decided_by, decided_at                                                             | 대상별 OPEN 이의는 1개 (부분 UNIQUE)                                           |
| `penalty_ledger`                              | P2   | id, study_id, season_id, user_id, entry_type(CHARGE/REVERSAL/PAYMENT/DEPOSIT/REFUND), amount, member_day_id, appeal_id, memo, created_by, created_at, idempotency_key                                             | **idempotency_key UNIQUE**. 추가만 가능 (UPDATE·DELETE 권한도 회수)            |
| `settlements`                                 | P2   | id, study_id, period_type(WEEK/SEASON), period_start, period_end, snapshot(JSONB), created_at                                                                                                                     | (study_id, period_type, period_start) UNIQUE                                   |
| `discord_links`                               | P3   | user_id, discord_user_id, linked_at                                                                                                                                                                               | discord_user_id UNIQUE                                                         |

**인덱스 (먼저 만들 것)**

- `member_days (study_id, study_day)`: 오늘 현황, 판정 배치
- `study_day_solves (study_id, study_day)`: 현황 상세
- `solve_records (user_id, received_at)`: 내 풀이
- `webhook_inbox (status, next_attempt_at)`, `notifications (status, next_attempt_at)`: 워커 조회
- `penalty_ledger (study_id, user_id)`: 정산
- `memberships (user_id)`: 내 스터디 목록

**마이그레이션 원칙**

- Flyway로만 스키마를 바꾸고, JPA는 `ddl-auto=validate`로 둬요.
- 이미 적용된 마이그레이션 파일은 고치지 않아요. 바꿀 게 있으면 새 버전을 추가해요.
- 데이터를 채우는 작업 (백필)은 다시 돌려도 결과가 같게 만들어요. 예: P2에서 P1 기간의 실패를 원장 CHARGE로 옮길 때 idempotency_key를 써요 (5.11).
