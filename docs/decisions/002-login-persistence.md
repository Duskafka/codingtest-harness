# 002. 로그인 유지 방식과 만료 정책

| 항목 | 내용 |
|---|---|
| 상태 | 제안 |
| 관련 | OD-01, S04, `docs/spec/api.md` 4.5, `docs/spec/security.md` 9.7, `docs/spec/data-model.md` 4.3(`spring_session`), D22, OD-03, 계획서 ADR-002의 일부 |
| 제안 | 에이전트, PR # |
| 날짜 | 2026-11-22 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

S04(로그인과 세션)가 OD-01에 막혀 있어요. 이 제안서는 세 가지를 정하기 위한 거예요.

1. **로그인 상태를 어디에 두고 어떻게 확인하나:** 서버 세션(DB) / JWT
2. **만료 정책:** api.md 4.5는 "세션 만료는 30일 롤링, 근거는 ADR-002에" 남기라고 해요
3. **쿠키 속성:** SameSite 값, 쿠키 이름 접두사

GitHub 로그인 방식(OD-02, 별도 OAuth App / GitHub App 사용자 인증)은 다루지 않아요. S04 단계 문서대로 S04 세션이 따로 제안서를 써요.

**스펙의 제약**

- 같은 출처: Nginx가 `/`에서 프론트를, `/api/*`·`/oauth2/*`·`/login/*`·`/webhooks/*`를 Spring으로 넘겨요. CORS가 없고 쿠키를 그대로 써요 (architecture.md 4.1).
- 서버 한 대 (D10, Lightsail 2GB). 혼자 개발하고 운영해요 (D3).
- DB는 Supabase 무료 플랜이고 커넥션 풀을 작게(5~10) 써요. "세션 테이블도 같은 DB라 요청마다 조회가 생기니 실제 사용량을 확인"하라고 되어 있어요 (D11, infra.md 9.4).
- Redis는 쓰지 않는 게 기본안이에요 (OD-03).
- 배포 중에도 로그아웃되지 않아야 해요 (infra.md 9.2 '배포 중 웹훅' 항목).
- GitHub access token은 신원 확인에만 쓰고 저장하지 않아요 (api.md 4.5).

**사람이 이번 세션에서 답한 조건 (2026-11-22)**

| 질문 | 답 |
|---|---|
| 클라이언트 | P1은 같은 출처의 웹/PWA만이에요. **Android·iOS 앱을 P1 이후에 만들 계획**이 있어요. 그래서 이 결정이 앱을 막지 않아야 해요. 스펙 2.4('모바일 네이티브 앱은 만들지 않음')와 달라 스펙 변경이 필요해요 (PR 본문) |
| 무효화 | 로그아웃, 계정 삭제(5.16), 탈취가 의심될 때 **즉시** 끊겨야 해요 |
| 추천 기준 | 보안과 운영 단순성 |

**사람이 제기한 전제: "JWT도 결국 서버에 유효성 확인용 저장소가 필요하지 않나"**

즉시 무효화가 조건이면 맞아요. JWT는 서명과 만료 시각만으로 검증되므로, 만료 전에 끊으려면 요청마다 서버 저장소(거부 목록, 사용자별 토큰 버전 등)를 확인해야 해요. OWASP JWT Cheat Sheet는 거부 목록을 "stateless session 무효화 문제의 우회책(workaround)"이라고 불러요. 그 저장소가 꼭 Redis일 필요는 없고 PostgreSQL로도 돼요. 그래서 이 결정의 실제 쟁점은 '상태가 있나 없나'가 아니라, **요청마다 확인하는 서버 상태를 어떤 모양(세션 행 / 토큰 + 거부 목록)으로 두고, 브라우저에 무엇을 맡기나**예요.

## 선택지

### ① 로그인 상태를 두는 방식

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. 서버 세션 + PostgreSQL (Spring Session JDBC)**. 웹은 HttpOnly 쿠키, 앱은 P1 이후 같은 세션을 헤더로 | 행 하나를 지우면 즉시 끊겨요. 사용자별 세션을 찾아 모두 지울 수 있어요(탈퇴, 모든 기기 로그아웃). 브라우저 JS가 세션 값을 읽을 수 없어요. Spring Security의 기본 동작(로그인 시 세션 ID 재발급, OAuth2 로그인 흐름)과 그대로 맞아요. 새 저장소나 서명 키가 없어요. 앱은 같은 세션을 헤더(`X-Auth-Token`)로 받을 수 있어요 | 요청마다 DB에서 세션을 읽어요 (작은 커넥션 풀, infra 9.4). 쿠키를 쓰므로 CSRF 방어가 필요해요 (스펙에 이미 있음). 서버가 여러 대가 되어도 같은 DB를 보면 되지만 DB 부하는 그대로예요 | 낮음~중간: 지금은 코드가 없어요. 나중에 B로 바꾸면 로그인 성공 처리, 토큰 엔드포인트, 프론트 요청 방식, 세션 테이블 제거 마이그레이션이 바뀌어요 |
| **B. 짧은 JWT 액세스 토큰 + DB의 리프레시 토큰(회전) + 요청마다 거부 목록·토큰 버전 확인** | 앱·외부 서비스에 널리 쓰는 형식이에요. 토큰 안에 사용자 ID 같은 정보를 담아 보낼 수 있어요 | 즉시 무효화를 위해 요청마다 DB를 확인하므로 A보다 DB 부하가 줄지 않아요. 여기에 서명 키 관리(비밀 보관·교체, 9.6), 알고리즘 고정 같은 JWT 규칙(RFC 8725), 리프레시 엔드포인트, 리프레시 회전과 재사용 감지(RFC 9700), 거부 목록 테이블과 정리 작업이 더해져요. 토큰을 HttpOnly 쿠키에 담으면 CSRF 방어가 여전히 필요하고, JS가 들고 있으면 XSS로 토큰이 밖으로 빠져나갈 수 있어요. OAuth2 로그인 흐름 자체는 기본으로 세션을 써서(인가 요청 저장) 바꾸려면 별도 저장소를 만들어야 해요 | 중간 |
| C. 짧은 JWT + 리프레시 토큰, 요청마다 확인하지 않음 | 요청마다 DB를 보지 않아요. 서버 여러 대에서 저장소 없이 검증해요 | **즉시 무효화 조건을 못 지켜요.** 로그아웃·탈퇴 뒤에도 액세스 토큰 수명 동안 요청이 통과해요. 나머지 단점은 B와 같아요 | 중간 |
| D. 서버 세션 + Redis | 세션 조회가 빠르고 DB 부하가 없어요 | OD-03 기본안(쓰지 않음)과 부딪혀요. 2GB 서버에 Redis를 하나 더 띄우고 백업·모니터링·영속 설정을 해야 해요. 지금 트래픽에서 얻는 게 작아요 | 중간 |
| E. 서버 세션 + 서버 메모리 (컨테이너 기본) | 설정이 가장 적어요 | 배포·재시작 때마다 모두 로그아웃돼요. infra 9.2와 S04 수용 기준('다시 띄워도 세션 유지')을 못 지켜요 | 낮음 |

**기준별 비교 (A, B만. C·D·E는 조건이나 기본안에 걸려요)**

| 기준 | A. DB 세션 | B. JWT + 거부 목록 |
|---|---|---|
| 즉시 무효화 | 세션 행 삭제 | 거부 목록 등록이나 토큰 버전 올리기 + 리프레시 토큰 폐기 |
| XSS가 생겼을 때 | 세션 값은 HttpOnly라 읽히지 않아요. 페이지가 열려 있는 동안 공격자가 요청을 대신 보내는 건 막지 못해요 (RFC 10017도 이건 앱 수준에서 못 막는다고 봐요) | 쿠키에 담으면 A와 같아요. JS가 들고 있으면 토큰이 밖으로 빠져나가 다른 곳에서도 쓰여요 |
| CSRF 방어 | 필요 (`csrf.spa()`) | 쿠키에 담으면 필요, 헤더로 보내면 불필요 |
| 앱(P1 이후) | 같은 세션을 헤더로 (`HeaderHttpSessionIdResolver`) | 헤더로 바로 |
| 서버가 요청마다 하는 일 | 세션 조회 | 서명 검증 + 거부 목록·토큰 버전 조회 |
| 새로 관리할 것 | 세션 테이블과 만료 정리 작업 | 서명 키, 리프레시 토큰 테이블, 거부 목록 테이블과 정리 작업 |
| 구현량 (Spring 기준) | Spring Session JDBC + Spring Security 기본 설정 | 토큰 발급(`NimbusJwtEncoder`), 검증(Resource Server), 리프레시·회전·재사용 감지, 로그인 성공 뒤 토큰 전달, 인가 요청 저장소를 직접 만들어요 |

### ② 만료 정책

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| a. 30일 롤링(유휴 만료)만 (스펙 4.5) | 매일 쓰는 서비스라 거의 다시 로그인하지 않아요. 알림을 눌러 들어왔을 때 로그인 화면이 뜨지 않아요 | 탈취된 세션은 공격자가 계속 쓰면 끝없이 이어져요. OWASP는 유휴 만료가 탈취된 세션을 막지 못한다고 해요 | 낮음 (설정 값) |
| b. 30일 롤링 + 절대 만료 (기간은 사람이 정함) | 탈취된 세션도 최대 수명이 생겨요. 롤링의 편함은 대부분 남아요 | 절대 만료마다 다시 로그인해요. Spring Session 공식 문서에서 절대 만료 기본 지원을 찾지 못했어요. 생성 시각으로 직접 검사해야 할 것으로 보여요 (추측) | 낮음 |
| c. 짧은 유휴 만료 (OWASP 예: 저위험 앱 15~30분) | 보안 권고 예시와 맞아요 | 하루 한 번 들어오는 서비스에서 거의 매번 로그인해야 해요. 사용자가 할 일을 줄이는 핵심 가치(D17)와 부딪혀요 | 낮음 |

참고: OWASP Session Management Cheat Sheet의 수치(유휴 15~30분, 절대 수 시간)는 업무용 웹앱을 예로 든 거예요. 이 서비스는 하루 한 번 알림으로 들어오는 사용 패턴이라 그대로 맞지 않아요. 그래서 기간보다 '절대 만료를 두는가'를 먼저 정하는 걸 제안해요.

### ③ 쿠키 속성

| 항목 | 선택지 | 내용 |
|---|---|---|
| SameSite | **Lax** (스펙 4.5) / Strict (RFC 10017 SHOULD) | Strict는 다른 사이트에서 넘어온 최상위 이동에 쿠키를 싣지 않아요. GitHub 로그인 뒤 콜백으로 돌아오는 요청이 바로 그 경우라, 세션에 저장된 OAuth2 인가 요청을 찾지 못해 로그인이 실패할 수 있어요 (추측, S04에서 테스트로 확인). CSRF는 어느 쪽이든 CSRF 토큰으로 막아요 |
| 이름 접두사 | `__Host-` 붙임 / 붙이지 않음 | RFC 10017이 SHOULD로 권해요. `Secure`, `Path=/`, `Domain` 없음이 조건인데 같은 출처 구성(4.1)과 맞아요. 하위 도메인에서 쿠키를 덮어쓰는 공격을 막아요. 로컬 개발(`http://localhost`)에서 브라우저가 받아 주는지는 확인하지 못했어요 (추측) |
| HttpOnly, Secure | 스펙대로 붙임 | RFC 10017 MUST |

## 추천과 이유

**① A (DB 세션), ② b (30일 롤링 + 절대 만료, 기간은 사람이 정함), ③ SameSite=Lax 유지 + `__Host-` 접두사**

- **① A:** '즉시 무효화'를 요구하면 B도 요청마다 DB를 봐야 해서, JWT의 가장 큰 장점(저장소 없이 검증)이 사라져요. 그러면 남는 차이는 B가 관리할 것(서명 키, 리프레시 회전, 거부 목록, 직접 만드는 로그인 흐름)이 더 많다는 점이에요. 서버 한 대, 같은 출처, 혼자 운영하는 조건에서는 A가 보안과 운영 모두 단순해요. RFC 10017도 개인정보를 다루는 브라우저 앱에는 서버가 토큰을 쥐고 브라우저에는 HttpOnly 쿠키 세션만 주는 구조(BFF)를 강하게 권해요. 우리 서버가 GitHub의 OAuth 클라이언트이고 브라우저는 세션 쿠키만 가지는 4.5 구성이 이 모양이에요.
- **앱(P1 이후)도 A로 막히지 않아요.** 앱은 RFC 8252대로 시스템 브라우저로 로그인하고, 같은 세션 저장소의 세션을 헤더로 보내면 돼요 (Spring Session `HeaderHttpSessionIdResolver`). 로그아웃·탈퇴는 웹과 같은 방법으로 즉시 끊겨요. 웹 쿠키와 앱 헤더를 한 서버에서 같이 받으려면 두 방식을 함께 해석하는 구성이 필요한데, 기본 제공되는지는 확인하지 못했어요 (추측: 직접 조합). 앱의 로그인 흐름(시스템 브라우저에서 앱으로 세션을 넘기는 방법)은 앱을 시작할 때 별도 제안서로 정하는 걸 제안해요.
- **② b:** 롤링만 두면 탈취된 세션이 끝없이 살 수 있어요. 절대 만료를 하나 두면 사용자 불편은 드물게 생기고, 최악의 경우가 기간 안으로 묶여요. 다시 로그인하는 비용은 GitHub에 이미 로그인되어 있으면 작을 것으로 보이지만 확인하지 못했어요 (추측). **사람이 정할 값:** 절대 만료 기간.
- **③:** Lax는 로그인 콜백을 깨지 않고, CSRF는 토큰으로 막아요. RFC 10017의 Strict 권고와 다른 부분이라 이유를 남겨요. `__Host-`는 같은 출처 구성에서 비용 없이 얻는 방어예요.

**다른 선택지를 고르면 바뀌는 것**

- ① B: `spring_session` 테이블 대신 리프레시 토큰·거부 목록 테이블, 서명 키 비밀(9.6 목록에 추가), 토큰 발급·검증·리프레시 엔드포인트, 프론트의 토큰 처리, OAuth2 인가 요청 저장소를 쿠키 등으로 바꾸는 작업. api.md 4.5, security.md 9.7, data-model 4.3, 기술 스택 표의 '세션' 행이 바뀌어요.
- ② a: 절대 만료 검사 코드가 없어져요. c: 만료 값만 바뀌고 사용 흐름(알림 → 로그인 화면)이 달라져요.
- ③ Strict: S04에서 로그인 콜백이 깨지지 않는지 먼저 확인해야 하고, 깨지면 인가 요청 저장 방식을 바꿔야 해요.

## 근거

모두 2026-11-22에 확인했어요.

- 버전: Spring Boot 4.1.3이 관리하는 Spring Security 7.1.3, Spring Session 4.1.2 ([spring-boot-dependencies 4.1.3 POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.3/spring-boot-dependencies-4.1.3.pom)). `spring-boot-starter-session-jdbc` 4.1.3이 Maven Central에 있어요.
- [Spring Session 4.1 — JDBC](https://docs.spring.io/spring-session/reference/4.1/configuration/jdbc.html): PostgreSQL 스키마, 만료 세션 정리 cron(`cleanupCron`), 속성 JSON 저장.
- [Spring Session 4.1 — `JdbcIndexedSessionRepository` API](https://docs.spring.io/spring-session/reference/4.1/api/java/org/springframework/session/jdbc/JdbcIndexedSessionRepository.html): `FindByIndexNameSessionRepository` 구현 (사용자별 세션 조회).
- [Spring Session 4.1 — REST 가이드](https://docs.spring.io/spring-session/reference/4.1/guides/java-rest.html): `HeaderHttpSessionIdResolver.xAuthToken()`으로 쿠키 대신 헤더.
- [Spring Security 7.1 — CSRF](https://docs.spring.io/spring-security/reference/7.1/servlet/exploits/csrf.html): SPA용 `csrf((csrf) -> csrf.spa())`.
- [Spring Security 7.1 — Session Management](https://docs.spring.io/spring-security/reference/7.1/servlet/authentication/session-management.html): 로그인 시 세션 고정 방지(`changeSessionId`가 기본).
- [Spring Security 7.1 — OAuth2 Client Authorization Grants](https://docs.spring.io/spring-security/reference/7.1/servlet/oauth2/client/authorization-grants.html): 인가 요청 기본 저장소는 `HttpSessionOAuth2AuthorizationRequestRepository`(HttpSession).
- [Spring Security 7.1 — `NimbusJwtEncoder` API](https://docs.spring.io/spring-security/reference/7.1/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html), [Resource Server JWT](https://docs.spring.io/spring-security/reference/7.1/servlet/oauth2/resource-server/jwt.html): B의 발급·검증 수단.
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html): 유휴·절대 만료 예시 값, 유휴 만료는 탈취된 세션을 막지 못함, 만료는 서버에서 강제.
- [OWASP JSON Web Token Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html): 거부 목록은 stateless session 무효화 문제의 우회책, 민감한 정보는 서버 쪽 불투명 토큰 뒤에.
- [RFC 10017 (BCP 212), OAuth 2.0 for Browser-Based Applications, 2026-08](https://www.rfc-editor.org/rfc/rfc10017): BFF 권고(6.1), 쿠키 규칙(6.1.3.2: Secure·HttpOnly MUST, SameSite=Strict·`__Host-` SHOULD), XSS로 브라우저 저장소의 토큰을 빼낼 수 있음(5장).
- [RFC 9700, OAuth 2.0 Security Best Current Practice](https://www.rfc-editor.org/rfc/rfc9700): 공개 클라이언트의 리프레시 토큰은 발신자 제한이나 회전 MUST (2.2.2, 4.14).
- [RFC 8725, JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725): 알고리즘 검증·고정, 충분한 키 엔트로피, 명시적 타입 (3장).
- [RFC 8252, OAuth 2.0 for Native Apps](https://www.rfc-editor.org/rfc/rfc8252): 네이티브 앱 로그인은 시스템 브라우저로, 임베디드 웹뷰 MUST NOT.

**확인하지 못한 것 (추측)**

- SameSite=Strict일 때 GitHub 콜백에서 세션 쿠키가 빠져 로그인이 실패하는지
- `http://localhost`에서 `__Host-` 쿠키를 브라우저가 받아 주는지
- Spring Session의 절대 만료 기본 지원 여부와 구현 방법
- 웹 쿠키와 앱 헤더를 한 서버에서 함께 해석하는 구성이 기본으로 되는지
- Spring Session JDBC가 요청마다 하는 쿼리 수(조회, 마지막 접근 시각 갱신)와 Supabase 무료 플랜에서의 부담
- GitHub에 로그인된 상태에서 다시 로그인할 때 동의 화면 없이 바로 돌아오는지

## 검증 방법

- **S04 테스트 (A로 정해지면):** 재시작 후 세션 유지(이미 수용 기준), 로그아웃 뒤 같은 쿠키로 요청하면 401, 한 사용자의 세션 여러 개를 사용자 기준으로 모두 지우면 모두 401(탈퇴·모든 기기 로그아웃의 바탕, 5.16), 쿠키 속성(HttpOnly·Secure·SameSite·이름 접두사), 로그인 시 세션 ID가 바뀜.
- **만료:** 유휴·절대 만료 경계 테스트. Spring Session이 주입한 시계를 쓰는지 S04에서 확인하고, 안 되면 만료 검사를 우리 코드(`Clock` 주입)로 둬요.
- **'추측' 항목:** S04에서 SameSite=Strict·`__Host-`를 실제 브라우저 흐름이나 테스트로 확인하고 결과를 PR에 적어요.
- **운영 측정:** 요청당 세션 테이블 쿼리 수, 커넥션 사용량, 만료 세션 정리 작업 소요 시간을 DB 쪽 통계로 봐요 (infra 9.4). 커넥션 풀이 모자라면 이 결정을 다시 열 조건이에요.

<!-- 여기부터 사람이 써요. 에이전트는 비워 둬요 -->

## 결정

## 검토 메모
