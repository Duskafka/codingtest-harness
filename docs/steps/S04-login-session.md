# S04. 로그인과 세션

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S01 |
| 결정 게이트 | OD-01 (로그인 유지 방식), OD-02 (GitHub 로그인 방식), OD-05 (Lombok), OD-06 (스키마 이름, 이 단계가 첫 마이그레이션이면) |
| 사람 할 일 | H-03 (실제 로그인 확인용 OAuth 설정, 자동 테스트에는 불필요) |
| T번호 | T19 기반 (권한 실패 응답) |

> 아래 내용은 계획서 기본안(OD-01 = DB 세션) 기준이에요. OD-01이나 OD-02가 다르게 정해지면 사람이 이 문서를 고친 뒤 시작해요. OD-02는 에이전트가 결정 제안서를 먼저 써요.

## 목표

GitHub로 로그인하고, 로그인 상태를 유지하고, `/api/me`로 내 정보를 본다.

## 읽을 문서

- `docs/spec/api.md`: 4.4의 계정 행, 4.5 전체
- `docs/spec/security.md`: 9.7 '로그인과 세션', '권한'
- `docs/spec/data-model.md`: 4.3의 `users`, `spring_session` 행
- `docs/spec/processes/5.02-github-connection.md`: '주의할 것' (OD-02 제안서를 쓸 때)
- `docs/spec/external-systems.md`: 4.6 마지막 항목

## 만들 것

1. **OD-02 결정 제안서 (먼저):** 별도 OAuth App과 GitHub App 사용자 인증을 비교한다. 받는 권한 범위, 설치와 로그인의 연결(5.2), 토큰 저장 여부, 운영 부담을 본다. 사람이 정하기 전에는 2번부터 하지 않는다.
2. **GitHub 로그인:** Spring Security OAuth2 Client. GitHub access token은 신원 확인에만 쓰고 저장하지 않는다. `users`를 `github_id` 기준으로 저장하거나 갱신한다.
3. **세션:** Spring Session JDBC로 DB에 저장한다 (D22). 쿠키는 HttpOnly, Secure, SameSite=Lax. 만료는 30일 롤링 (4.5).
4. **CSRF:** SPA용 공식 설정. 토큰을 쿠키로 내려 주고 SPA가 `X-XSRF-TOKEN` 헤더로 돌려보낸다.
5. **엔드포인트:** `GET /api/me`, `POST /logout`. 4.4의 나머지 계정 엔드포인트는 그 단계에서 만든다.
6. **경로별 보안:** `/webhooks/**`는 CSRF와 세션에서 빼고 서명으로만 인증한다는 자리를 만든다 (서명 검증은 S06). `/actuator/health`는 공개.
7. **인증 실패 응답:** 401과 403을 `application/problem+json`으로 (S01 형식).

## 만들지 않을 것

- 스터디 권한 검사 `StudyAccess` (S05)
- 계정 삭제 `DELETE /api/me` (S15)
- `/admin/**` (S15)

## 수용 기준

- [ ] 로그인 콜백을 거치면 `users` 행이 생기고, 같은 `github_id`로 다시 로그인하면 행이 늘지 않고 갱신된다
- [ ] 로그인하지 않고 `GET /api/me` → 401 `application/problem+json`
- [ ] 세션 쿠키에 HttpOnly, Secure, SameSite=Lax가 붙는다
- [ ] CSRF 토큰 없이 로그인한 사용자가 POST → 403. `X-XSRF-TOKEN` 헤더가 맞으면 통과한다
- [ ] 애플리케이션을 다시 띄워도 세션이 유지된다 (세션이 DB에 있음, D22)
- [ ] GitHub access token이 DB와 로그 어디에도 남지 않는다
- [ ] `/webhooks/**`는 CSRF 없이 요청이 컨트롤러까지 간다 (서명 검증 전 단계 확인용)

## 주의할 것

- Spring Security의 SPA용 CSRF 설정은 공식 문서의 'Single-Page Applications' 절을 그대로 따른다. 대충 하면 POST가 전부 403이 난다 (4.5).
- Boot 4가 관리하는 Spring Security 버전의 문서를 본다. 이전 버전 예제의 설정 방식이 바뀌었을 수 있다.
- 실제 GitHub에 접속하는 테스트는 만들지 않는다. OAuth2 로그인은 Spring Security 테스트 지원으로 흉내 낸다.

## 판단 기록이 필요한 곳

- **결정 제안서:** OD-02 (위 1번)
- **판단 기록:** 세션 30일 롤링 구현 방식과 트레이드오프 (4.5가 근거를 남기라고 함), `users` 갱신 항목
