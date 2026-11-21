# 용어와 코드 이름

> 스펙 문서예요. 에이전트는 고치지 않아요.
>
> 뜻은 `domain-rules.md` 3.1이 기준이에요. 이 표는 코드에서 같은 개념을 같은 이름으로 부르기 위한 권장 이름이에요. 처음 쓰는 단계에서 다르게 해야 할 이유가 있으면 판단 기록에 남기고 '꼭 봐 주세요'로 표시해요.

## 개념

| 용어 | 코드 이름 (권장) | 메모 |
|---|---|---|
| 스터디 | `Study` | 모드: `GROUP`, `SOLO` |
| 스터디 규칙 | `StudyRule` | 버전마다 한 행. `effectiveFrom` (R2) |
| 기수 | `Season` | 상태: `SCHEDULED`, `ACTIVE`, `SETTLING`, `CLOSED` (3.4) |
| 멤버십 | `Membership` | 역할: `OWNER`, `MEMBER`. `judgedFrom`, `leftAt` |
| 초대 | `Invitation` | 토큰 원문은 저장하지 않고 `tokenHash`만 |
| 스터디 시간대 | `zoneId` (`ZoneId`) | 기본 `Asia/Seoul` |
| 하루 기준 시각 | `dayBoundary` (`LocalTime`) | 기본 00:00, 범위 00:00~06:00 |
| 유예 | `graceMinutes` | 기본 30 |
| 알림 오프셋 | `reminderOffsetMinutes` | 기본 120 |
| 하루 목표 | `dailyGoal` | 기본 1 |
| 스터디 일 | `studyDay` (`LocalDate`) | `[D 기준 시각, D+1 기준 시각)` |
| 시간 계산 | `StudyCalendar` | `common` 모듈. 모든 시간 계산을 여기서 |
| 멤버 일 | `MemberDay` | 멤버 × 스터디 일. 진행 상황과 판정 결과 |
| 판정 결과 | `JudgmentResult` | `SUCCESS`, `FAIL`, `EXEMPT_REST`(쉬는 날), `EXEMPT_TICKET`(면제권). 미판정은 `null`. '기수 밖'(R9)을 어떻게 남길지는 OD-07과 함께 정해요 |
| 풀이 기록 | `SolveRecord` | 출처: `GITHUB`, `SELF_REPORT`, `RECOVERY`. 상태: `CONFIRMED`, `PENDING_IDENTIFICATION`(확인 대기), `IGNORED` |
| 스터디별 집계 | `StudyDaySolve` | R5 유니크 제약을 거는 곳 |
| 문제 식별자 | `ProblemRef` | (플랫폼, 문제 번호). 키는 항상 둘 다 |
| 플랫폼 | `Platform` | 예: `BOJ`, `PROGRAMMERS`, `LEETCODE`. 실제 값은 S03에서 정함 |
| 수동 인증 | `SelfReport` | 상태: `ACCEPTED`, `PENDING_APPROVAL`, `WITHDRAWN`, `REJECTED` (3.4) |
| 면제권 | 별도 엔티티 없음 | `MemberDay.exemptionUsedAt`으로 기록 (4.3) |
| 이의 | `Appeal` | 종류: `JUDGMENT`, `SELF_REPORT_OBJECTION`. 상태: `OPEN`, `ACCEPTED`, `REJECTED`, `EXPIRED` (P2) |
| 원장 기록 | `LedgerEntry` | 종류: `CHARGE`, `REVERSAL`, `PAYMENT`, `DEPOSIT`, `REFUND` (P2) |
| 정산 | `Settlement` | 기간: `WEEK`, `SEASON` (P2) |
| 웹훅 수신함 | `WebhookInbox` | 출처: `GITHUB`, `DISCORD`, `KAKAO`. 상태: `RECEIVED`, `PROCESSED`, `IGNORED`, `FAILED`, `DEAD` |
| GitHub 설치 | `GitHubInstallation` | `installationId` |
| 연결 레포 | `LinkedRepository` | 이름이 아니라 `repoId`로 추적 |
| 알림 | `Notification` | 종류와 `dedupeKey`는 5.8 표. 상태: `PENDING`, `SENDING`, `SENT`, `FAILED`, `SKIPPED`, `EXPIRED` |
| 푸시 구독 | `PushSubscription` | `endpoint` UNIQUE |

## 단계 표시

| 표시 | 뜻 |
|---|---|
| P0 | 코드 없이 사람이 하는 검증 단계 (모집, 인터뷰, 샘플 수집). 문서에 'P0에서 확인'이 남아 있으면 사람 할 일이에요 |
| P1 | 첫 운영 루프 |
| P2 | 신뢰성과 정산 |
| P3 | 유지 기능 |
| P4 | 운영과 개선 |
| S01~ | 작업 단계 (`docs/steps/`). P와 S는 다른 축이에요. S 하나는 P 하나에 속해요 |

## 번호 체계

| 표기 | 뜻 | 위치 |
|---|---|---|
| R1~R16 | 도메인 규칙 | `domain-rules.md` 3.2 |
| Q1~Q12 | 도메인의 열린 질문 | `domain-rules.md` 3.6 |
| T1~T30 | 꼭 있어야 할 테스트 | `testing.md` 8.2 |
| D1~D24 | 확정 결정 | `docs/decisions/README.md` |
| OD-01~ | 열린 결정 | `docs/decisions/README.md` |
| H-00~ | 사람 할 일 | `docs/steps/README.md` |
| [S4]~[S61] | 출처 | `references.md` |
