# S06. 웹훅 수신 기반

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S01 |
| 결정 게이트 | OD-05, OD-06 (이 단계가 첫 엔티티·첫 마이그레이션이면) |
| 사람 할 일 | 없음 |
| T번호 | T1, T2 |

## 목표

GitHub 웹훅을 위조 없이 받고, 같은 전달은 한 번만 기록한다. P1은 '수신 기록 후 같은 요청 안에서 처리'다 (D21). 이벤트별 처리기는 S07, S08이 붙인다.

## 읽을 문서

- `docs/spec/processes/5.03-push-webhook.md`: P1 다이어그램, 상태 다이어그램, 응답 코드 규칙, 주의할 것
- `docs/spec/pseudocode.md`: 'push 웹훅 처리', '서명 검증 (Java)'
- `docs/spec/security.md`: 9.7 '받는 요청'
- `docs/spec/infra.md`: 9.6 비밀 관리 표의 '웹훅 시크릿' 행
- `docs/spec/data-model.md`: `webhook_inbox`
- `docs/spec/external-systems.md`: GitHub 행
- `docs/decisions/003-lombok.md`: '결정', B의 허용 목록과 강제 방법 (이 단계가 첫 엔티티면)
- `docs/decisions/004-db-schema-name.md`: '결정', '적용 방법' (이 단계가 첫 마이그레이션이면)

## 만들 것

1. **수신 엔드포인트:** `POST /webhooks/github`. 본문을 `byte[]`로 받아 서명부터 확인하고, 그다음에 파싱한다.
2. **서명 검증:** HMAC-SHA256, `MessageDigest.isEqual`로 상수 시간 비교. 시크릿을 교체하는 동안 옛 값과 새 값을 모두 받아들인다 (9.6). 시크릿은 환경 변수로만 받는다.
3. **이벤트 거르기:** `push`, `installation`, `installation_repositories`, `ping`만 받는다. 나머지는 204.
4. **수신함 저장:** `webhook_inbox` INSERT를 별도 트랜잭션으로 먼저 커밋한다. `(source, delivery_id)` UNIQUE로 같은 전달을 한 번만 기록한다.
5. **처리기 연결:** 이벤트 종류 → 처리기 인터페이스. 같은 요청 안에서 새 트랜잭션으로 처리하고, 성공하면 `PROCESSED`(또는 `IGNORED`), 실패하면 `FAILED`와 오류를 남긴다. 처리가 실패해도 응답은 202다.
6. **응답 코드:** 5.3의 응답 코드 규칙 표대로.
7. **본문 크기 제한:** 큰 본문은 거절한다 (5.3, 예: 5MB).
8. **Lombok·스키마 (이 단계가 첫 엔티티·첫 마이그레이션이면):** `build.gradle`에 Lombok(`compileOnly`·`annotationProcessor`, 버전은 Boot BOM)과 레포 루트 `lombok.config`(허용 목록 밖 기능은 `flagUsage = error`)를 더한다 (결정 제안서 003). 앱 테이블은 `studybot` 스키마에 두고, 설정 방법(Flyway·Hibernate·search_path)은 판단 기록으로 정한다 (결정 제안서 004).

## 만들지 않을 것

- installation·push 처리기 (S07, S08)
- 비동기 워커, 재시도, `DEAD`, 누락 복구 (P2)
- payload 14일 뒤 삭제 (S15)

## 수용 기준

- [ ] T1: 서명이 없거나 틀린 요청 → 401, 수신함에 행이 없다
- [ ] 16진수가 아닌 서명 → 401
- [ ] T2: 같은 `X-GitHub-Delivery`를 두 번 받으면 수신함은 1행, 처리기는 한 번만 불린다
- [ ] `ping` → 200 또는 204, 관심 없는 이벤트 → 204
- [ ] 저장에 성공하면 202
- [ ] 처리기가 예외를 던지면 202이고, 수신함 상태는 `FAILED`, 오류 내용이 남는다
- [ ] DB에 저장할 수 없으면 503 (GitHub에 실패로 남아야 나중에 재전송할 수 있다)
- [ ] 시크릿 교체 중에는 옛 시크릿과 새 시크릿으로 서명한 요청이 모두 통과한다
- [ ] 수신 시각(`received_at`)은 주입된 `Clock`에서 온다

## 주의할 것

- 10초 안에 응답해야 한다. 처리 안에서 GitHub API를 부르지 않는다 [S33].
- Spring이 본문을 객체로 먼저 바꾸면 서명이 맞지 않는다 [S36].
- 테스트용 payload는 실제 GitHub payload 모양을 따른다. `src/test/resources/github-payloads/`에 둔다. 커밋 작성자 이메일 같은 개인정보는 가짜 값으로 바꾼다.

## 판단 기록이 필요한 곳

- 처리기 인터페이스 모양 (S07·S08·P2 워커가 쓴다, '꼭 봐 주세요')
- 수신함 저장과 처리의 트랜잭션 경계
