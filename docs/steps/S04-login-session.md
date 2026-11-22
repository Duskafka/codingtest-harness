# S04. 로그인과 세션

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S01 |
| 결정 게이트 | OD-01 (로그인 유지 방식), OD-02 (GitHub 로그인 방식), OD-05 (Lombok), OD-06 (스키마 이름, 이 단계가 첫 마이그레이션이면) |
| 사람 할 일 | H-02 (개발용 GitHub App 등록. 실제 로그인 확인용 콜백 URL·client secret 포함, 자동 테스트에는 불필요) |
| T번호 | T19 기반 (권한 실패 응답) |

> OD-01은 DB 세션으로 확정됐어요 (결정 제안서 002). OD-05·OD-06도 확정됐어요 (결정 제안서 003·004). OD-02는 웹훅용 GitHub App의 사용자 인증으로 확정됐어요 (결정 제안서 005). 2번부터 시작해요.

## 목표

GitHub로 로그인하고, 로그인 상태를 유지하고, `/api/me`로 내 정보를 본다.

## 읽을 문서

- `docs/spec/api.md`: 4.4의 계정 행, 4.5 전체
- `docs/spec/security.md`: 9.7 '로그인과 세션', '권한'
- `docs/spec/data-model.md`: 4.3의 `users`, `spring_session` 행
- `docs/spec/processes/5.02-github-connection.md`: '주의할 것'
- `docs/spec/external-systems.md`: 4.6 로그인 항목 (콜백 URL, 토큰 만료, 설치 중 사용자 인증)
- `docs/decisions/002-login-persistence.md`: '결정' (세션·만료·쿠키)
- `docs/decisions/003-lombok.md`: '결정', B의 허용 목록과 강제 방법
- `docs/decisions/004-db-schema-name.md`: '결정', '적용 방법' (Spring Session JDBC 항목 포함)
- `docs/decisions/005-github-login-method.md`: '결정', '추천과 이유'의 '양쪽 공통'

## 만들 것

1. **OD-02 결정 제안서 (먼저):** 별도 OAuth App과 GitHub App 사용자 인증을 비교한다. 받는 권한 범위, 설치와 로그인의 연결(5.2), 토큰 저장 여부, 운영 부담을 본다. 사람이 정하기 전에는 2번부터 하지 않는다. → 결정 제안서 005로 확정 (B).
2. **GitHub 로그인:** Spring Security OAuth2 Client. 웹훅용 GitHub App의 사용자 인증으로(결정 제안서 005), App의 client ID와 client secret(`.env`, infra 9.6)을 쓴다. GitHub access token은 신원 확인에만 쓰고, 리프레시 토큰까지 저장하지 않는다. `users`를 `github_id` 기준으로 저장하거나 갱신한다.
3. **세션:** Spring Session JDBC로 DB에 저장한다 (D22). 세션 쿠키는 HttpOnly, Secure, SameSite=Lax이고 이름은 `__Host-Http-` 접두사로 시작한다 (로컬 개발·브라우저 지원이 안 되면 `__Host-`, 확인 결과를 PR에 적는다). 만료는 30일 롤링 (4.5, 결정 제안서 002).
4. **CSRF:** SPA용 공식 설정. 토큰을 쿠키로 내려 주고 SPA가 `X-XSRF-TOKEN` 헤더로 돌려보낸다.
5. **엔드포인트:** `GET /api/me`, `POST /logout`. 4.4의 나머지 계정 엔드포인트는 그 단계에서 만든다.
6. **경로별 보안:** `/webhooks/**`는 CSRF와 세션에서 빼고 서명으로만 인증한다는 자리를 만든다 (서명 검증은 S06). `/actuator/health`는 공개.
7. **인증 실패 응답:** 401과 403을 `application/problem+json`으로 (S01 형식).
8. **Lombok·스키마:** 엔티티는 결정 제안서 003의 허용 목록만 쓰고, `users`·`spring_session` 테이블은 `studybot` 스키마에 둔다 (결정 제안서 004).
    - 이 단계가 첫 엔티티면: `build.gradle`에 Lombok(`compileOnly`·`annotationProcessor`·`testCompileOnly`·`testAnnotationProcessor`, 버전은 Boot BOM)과 레포 루트 `lombok.config`(`config.stopBubbling = true`, 허용 목록 밖 기능은 `flagUsage = error`)를 더한다.
    - 이 단계가 첫 마이그레이션이면: 설정 방법(Flyway·Hibernate·Spring Session·search_path)을 판단 기록으로 정한다.
    - 앞 단계(예: S06)가 이미 더했으면 그 설정과 판단 기록을 따르고, Spring Session JDBC 테이블도 같은 방식으로 `studybot`에 둔다.

## 만들지 않을 것

- 스터디 권한 검사 `StudyAccess` (S05)
- 계정 삭제 `DELETE /api/me` (S15)
- `/admin/**` (S15)

## 수용 기준

- [x] 로그인 콜백을 거치면 `users` 행이 생기고, 같은 `github_id`로 다시 로그인하면 행이 늘지 않고 갱신된다
- [x] 로그인하지 않고 `GET /api/me` → 401 `application/problem+json`
- [x] 세션 쿠키 이름이 `__Host-Http-`로 시작하고(안 되면 `__Host-`, 결정 제안서 002), HttpOnly, Secure, SameSite=Lax가 붙는다
- [x] CSRF 토큰 없이 로그인한 사용자가 POST → 403. `X-XSRF-TOKEN` 헤더가 맞으면 통과한다
- [x] 애플리케이션을 다시 띄워도 세션이 유지된다 (세션이 DB에 있음, D22)
- [x] GitHub access token이 DB와 로그 어디에도 남지 않는다
- [x] `/webhooks/**`는 CSRF 없이 요청이 컨트롤러까지 간다 (서명 검증 전 단계 확인용)

## 주의할 것

- Spring Security의 SPA용 CSRF 설정은 공식 문서의 'Single-Page Applications' 절을 그대로 따른다. 대충 하면 POST가 전부 403이 난다 (4.5).
- Boot 4가 관리하는 Spring Security 버전의 문서를 본다. 이전 버전 예제의 설정 방식이 바뀌었을 수 있다.
- 실제 GitHub에 접속하는 테스트는 만들지 않는다. OAuth2 로그인은 Spring Security 테스트 지원으로 흉내 낸다.

## 판단 기록이 필요한 곳

- **결정 제안서:** OD-02 (위 1번, 005로 확정)
- **판단 기록:** 세션 30일 롤링 구현 방식 (만료 정책은 결정 제안서 002에서 정함), `users` 갱신 항목, (첫 엔티티면) `@Builder` 쓰는 방식 (결정 제안서 003), (첫 마이그레이션이면) 스키마 설정 방법 (결정 제안서 004), 로그인 토큰을 남기지 않는 구성·인가 요청에 PKCE가 붙는지 확인·신원 확인 직후 토큰을 취소할지 (결정 제안서 005 '양쪽 공통')
