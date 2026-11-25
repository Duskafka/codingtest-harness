# S13. 알림 기반과 웹 푸시

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S11 (S12 뒤를 권장) |
| 결정 게이트 | OD-11 (웹 푸시 라이브러리). 에이전트가 결정 제안서를 먼저 써요 |
| 사람 할 일 | H-04 (운영용 VAPID 키, 배포 전) |
| T번호 | T14, T15, T16, T17, T28, T19 |

## 목표

아직 못 푼 사람에게만, 마감 전에 한 번, 확실히 알린다. 알림 생성과 발송을 나눈 outbox 구조를 만들어 이후 알림(디스코드, 결과, 정산)이 같은 구조를 쓰게 한다.

## 읽을 문서

- `docs/spec/processes/5.08-deadline-reminder.md` 전체
- `docs/spec/pseudocode.md`: '마감 전 알림'
- `docs/spec/data-model.md`: `notifications`, `push_subscriptions`, `studies.last_reminded_day`
- `docs/spec/security.md`: 9.7 '보내는 요청 (SSRF 방지)'
- `docs/spec/external-systems.md`: 웹 푸시 행
- `docs/spec/architecture.md`: '알려진 충돌과 빈 곳' 5번, 10번

## 만들 것

1. **OD-11 결정 제안서 (먼저):** Java 웹 푸시 라이브러리를 유지보수 상태, Java 25·Boot 4 호환, VAPID(RFC 8292)와 메시지 암호화(RFC 8291) 지원으로 비교한다. 사람이 정하기 전에는 웹 푸시 발송(6번)을 만들지 않는다. 1~5번은 진행해도 된다.
2. **알림 생성 스케줄러 (1분마다, ShedLock):** 알림 시각은 그날 D의 마감에서 D 규칙의 알림 오프셋을 뺀 시각이다. `common.time.RuleVersionCalendar`에 `reminderAt(D)` = `startOf(D+1)` − D 규칙의 알림 오프셋을 더해 센다. 오늘 바꾼 오프셋은 다음 스터디 일부터 적용된다 (R2). 알림 시각이 지났는데 아직 만들지 않은 (스터디, 날)을 찾아 대상(미달성, 면제권 없음, 쉬는 날 아님)에게 `REMINDER`, 대상이 있으면 방장에게 `SHARE_PROMPT`를 만든다. `dedupe_key` UNIQUE, 만료 = 그날 마감. 마감이 지났으면 만들지 않는다. 진행 지점은 `studies.last_reminded_day`.
3. **발송기 (5초마다):** `PENDING`이고 시각이 된 행을 `FOR UPDATE SKIP LOCKED`로 가져와 `SENDING`(잠금 만료 시각)으로 먼저 커밋한다. 만료됐으면 `EXPIRED`, 그새 풀었거나 면제권을 썼으면 `SKIPPED`. 잠금이 만료된 `SENDING`은 `PENDING`으로 되돌린다.
4. **규칙 변경 알림 `RULE_CHANGED`:** 규칙이 바뀌면 멤버에게. study → notification 방향이라 의존 규칙에 맞는 방식으로 (알려진 충돌 5번).
5. **푸시 구독 API:** `GET /api/push/vapid-public-key`, `POST /api/me/push-subscriptions`, `DELETE /api/me/push-subscriptions/{id}`. 구독 주소는 알려진 푸시 서비스 도메인만 받는다 (T28).
6. **웹 푸시 발송:** 암호화한 푸시, TTL은 그날 마감까지, `Urgency`, `Topic`(같은 날 알림은 새것이 옛것을 덮음). 201 → `SENT`, 404·410 → 구독 삭제와 `FAILED`, 429·5xx → 재시도 시각을 정하고 `PENDING`. payload는 작게, 개인정보는 최소로.
7. **지표:** 발송 성공률, 구독 정리 건수.

## 만들지 않을 것

- 디스코드 발송, `DISCORD_STATUS`, `DAILY_RESULT` (S14)
- 서비스 워커와 알림 권한 화면 (S16)
- 두 번째 알림, 방해 금지 시간 (P3)

## 수용 기준

- [ ] T14: 알림 대상 계산에서 달성한 멤버, 면제권을 쓴 멤버, 쉬는 날이 빠진다
- [ ] T15: 알림 생성 스케줄러가 두 번 돌아도 알림은 1개다
- [ ] T16: 발송 직전에 목표를 달성했으면 `SKIPPED`
- [ ] T17: 웹 푸시 서비스가 410을 주면 구독이 삭제된다
- [ ] 429·5xx면 재시도 시각이 뒤로 밀리고 시도 횟수가 오른다
- [ ] 서버가 알림 시각부터 마감 뒤까지 꺼져 있었다면, 켜질 때 그날 알림을 만들지 않거나 `EXPIRED`로 둔다
- [ ] T28: 알려진 푸시 서비스가 아닌 구독 주소는 거절된다
- [ ] 발송 중에 서버가 죽은 것처럼 `SENDING`이 남으면, 잠금 만료 뒤 다시 `PENDING`이 된다
- [ ] 웹 푸시 서비스는 WireMock으로 테스트한다
- [ ] T19: 남의 구독을 지울 수 없다

## 주의할 것

- '정확히 한 번'은 불가능하다. 목표는 중복 생성 금지 + 실패 시 재시도(적어도 한 번) + 발송 직전 재확인이다 (5.8).
- VAPID 키는 한 번 만들면 바꾸지 않는다. 개인키는 환경 변수로만 받는다. 테스트에는 테스트 안에서 만든 키를 쓴다.

## 판단 기록이 필요한 곳

- **결정 제안서:** OD-11 (위 1번)
- 재시도 백오프 값, 발송기 한 번에 가져올 개수
- `RULE_CHANGED`를 만드는 방식 (알려진 충돌 5번)
