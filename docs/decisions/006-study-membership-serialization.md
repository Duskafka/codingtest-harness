# 006. 스터디 멤버십 변경의 동시성: 스터디 행 잠금

| 항목 | 내용 |
|---|---|
| 상태 | 제안 |
| 관련 | S05, 5.1 '정원 동시성', T20, R14 (위임·탈퇴), 이후 S15(계정 삭제)·P2(멤버 내보내기) |
| 제안 | 에이전트, S05 PR |
| 날짜 | 2026-11-23 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

S05는 초대 수락, 방장 위임, 스터디 탈퇴로 멤버십을 바꿔요. 동시에 들어오는 요청끼리 지켜야 할 것이 셋이에요.

1. **정원 30명** (5.1). 스터디 하나에 초대가 여러 개일 수 있어요.
2. **초대 사용 횟수** (5.1, T20). 5.1은 `UPDATE invitations SET used_count = used_count + 1 WHERE id = ? AND used_count < max_uses`의 영향받은 행 수로 판단하라고 해요.
3. **같은 사람이 두 번 수락하면 멤버십 하나, 사용 횟수도 한 번** (5.1 '이미 멤버면 그대로 성공'). 위임하는 사이에 상대가 탈퇴하면 안 되는 것도 같은 종류예요 (R14).

5.1의 조건부 UPDATE는 2번만 지켜요. 초대 두 개에 각자 자리가 남아 있으면 마지막 한 자리를 두 사람이 동시에 가져가 31명이 될 수 있어요. 같은 사람이 동시에 두 번 누르면 둘 다 '아직 멤버 아님'을 보고 사용 횟수를 두 번 써요. 이때 두 번째 INSERT는 부분 UNIQUE에 막혀 500이 돼요.

제약: Supabase PostgreSQL 17, Session pooler (D11). Redis는 쓰지 않아요 (OD-03). 트래픽은 작고, 스터디 하나의 멤버십 변경은 드물어요.

## 선택지

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| A. 스터디 행 쓰기 잠금 (`@Lock(PESSIMISTIC_WRITE)`): 수락·위임·탈퇴가 먼저 스터디 행을 잠그고, 그 안에서 정원·멤버 여부를 보고 저장. 5.1의 조건부 UPDATE는 그대로 함께 씀 | 세 가지를 한 규칙으로 지킴. 컬럼 추가 없음. 잠금 범위가 스터디 하나라 다른 스터디는 기다리지 않음 | 같은 스터디의 멤버십 변경이 한 줄로 섬. 앞으로 멤버십을 바꾸는 코드(S15 계정 삭제, P2 멤버 내보내기)도 같은 잠금을 잡아야 함 | 낮음 (코드만) |
| B. `studies.member_count` 컬럼 + 조건부 UPDATE (`member_count < 30`) | 잠금을 오래 잡지 않음 | 컬럼 추가. 탈퇴·삭제와 숫자를 맞춰야 함. 같은 사람의 동시 두 번 수락은 따로 막아야 함 | 중간 (마이그레이션) |
| C. SERIALIZABLE 격리 + 재시도 | 코드에 잠금이 드러나지 않음 | 직렬화 실패 재시도 처리 필요. pooler·JPA에서 동작 확인이 더 필요 | 중간 |
| D. 5.1의 조건부 UPDATE만 | 가장 단순, 스펙 문장 그대로 | 정원 초과·사용 횟수 두 번 쓰기를 막지 못함 (T20 '정원을 넘지 않는다' 위반 가능) | 낮음 |

## 추천과 이유

**A.** 정원·사용 횟수·같은 사람의 두 번 수락·위임 중 탈퇴를 한 가지 규칙("스터디의 멤버십을 바꾸려면 먼저 스터디 행을 잠근다")으로 지켜요. 스터디 하나의 멤버십 변경은 드물어서 한 줄로 서도 기다림이 거의 없어요. 5.1의 조건부 UPDATE도 함께 써서, 초대를 읽은 뒤 철회·만료된 경우까지 UPDATE 시점의 행으로 다시 걸러요.

B를 고르면 멤버 수를 두 군데(행 개수와 컬럼)에서 관리하게 되고, C를 고르면 재시도 코드가 서비스마다 생겨요.

## 근거

- 5.1 '정원 동시성', T20 (testing.md 8.2)
- Spring Data JPA [Locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html): 쿼리 메서드에 `@Lock`을 붙여 잠금 모드를 정함. `PESSIMISTIC_WRITE`·`@Query`와 함께 쓰는 예는 문서에 없어 동작은 아래 테스트로 확인
- Hibernate 7.4.15 `PostgreSQLDialect.getForUpdateString()`은 `" for no key update"`를 돌려줌 (jar를 javap로 확인). `FOR NO KEY UPDATE`끼리는 서로 기다리고, 다른 행이 이 행을 FK로 참조하는 것(`KEY SHARE`)은 막지 않음 ([PostgreSQL 17 행 잠금](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS))
- 구현: `StudyRepository#findByIdForUpdate`, `InvitationService#accept`, `StudySettingsService#update`, `MembershipService#leave`

## 검증 방법

- `InvitationConcurrencyIntegrationTest`: 정원 29/30에서 서로 다른 초대로 두 명 동시 수락 → 한 명만 성공·30명, maxUses 1을 두 명 동시 → 한 명만, 같은 사람 동시 두 번 → 멤버십 하나·사용 횟수 1
- 잠금을 일반 조회 + 200ms 대기로 바꾸면 정원·두 번 수락 테스트가 실패하는 것을 확인함 (S05 PR 실행 기록)
- `InvitationUseCountIntegrationTest`: 조건부 UPDATE가 철회·만료·소진을 다시 거름
- 운영: 수락 응답 시간. 스터디당 멤버십 변경이 몰리는 일이 생기면 다시 봄

<!-- 여기부터 사람이 정해요. 사람이 요청하면 에이전트가 받아 적고, '결정' 절 맨 위에 그렇게 표시해요 (docs/workflow.md 3장). 요청이 없으면 에이전트는 비워 둬요 -->

## 결정

## 검토 메모
