# 009. 웹 푸시 Java 라이브러리

| 항목 | 내용 |
|---|---|
| 상태 | 확정 |
| 관련 | OD-11, S13 (만들 것 1·5·6), ADR-008, `docs/spec/processes/5.08-deadline-reminder.md`, `docs/spec/security.md` 9.7 '보내는 요청 (SSRF 방지)', `docs/spec/infra.md` 9.6 'VAPID 개인키', `docs/spec/architecture.md` 기술 스택·6장 ④⑥ |
| 제안 | 에이전트, PR #34 |
| 날짜 | 제안 2026-11-27 / 결정 2026-11-27 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

S13(알림 기반과 웹 푸시)은 OD-11에 걸려 있어요. 단계 문서의 '만들 것' 1이 이 제안서예요. 사람이 정하기 전에는 웹 푸시 발송(만들 것 6)을 만들지 않아요. 정할 것은 **브라우저 푸시 서비스로 보낼 요청을 무엇으로 만드나**예요. 요청에는 두 가지가 들어가요.

- **VAPID (RFC 8292):** 서버 신원을 밝히는 `Authorization: vapid t=<JWT>, k=<공개키>` 헤더. JWT는 ES256(P-256) 서명이고 `aud`(푸시 서비스 출처), `exp`(24시간 안), `sub`(연락처)를 담아요.
- **메시지 암호화 (RFC 8291, RFC 8188 `aes128gcm`):** 구독의 `p256dh`(P-256 공개키)·`auth`(16바이트)로 payload를 암호화해요. ECDH, HKDF(HMAC-SHA-256), AES-128-GCM을 써요.

헤더 `TTL`·`Urgency`·`Topic`(RFC 8030)은 라이브러리가 붙여 주든 우리가 붙이든 단순해요.

**스펙의 제약**

- Java 25, Spring Boot 4.1, Jackson 3을 써요. 라이브러리를 더하면 Boot 4 지원 버전인지 확인하고 근거를 PR에 적어요 (architecture 기술 스택, workflow 6장).
- 보내는 요청은 리다이렉트를 따라가지 않고, 응답 시간·크기를 제한해요. 구독 주소는 알려진 푸시 서비스 도메인만 받아요 (9.7, T28). 이미 GitHub API 클라이언트가 JDK `HttpClient`(리다이렉트 끔, 연결 3초, 응답 10초)를 써요 (`github.api.GithubApiClient`).
- 외부 호출은 전용 스레드 풀, 타임아웃, 백오프 재시도로 격리해요 (architecture 6장 ⑥). 자동 테스트는 웹 푸시 서비스를 WireMock으로 흉내 내요 (external-systems, S13 수용 기준).
- VAPID 키는 한 번 만들면 바꾸지 않아요. 개인키는 환경 변수로만 받고, 테스트에는 테스트 안에서 만든 키를 써요 (S13 '주의할 것', 9.6).
- 5.8의 응답 처리: 201 → SENT, 404·410 → 구독 삭제와 FAILED, 429·5xx → 재시도. 상태 코드를 우리가 직접 볼 수 있어야 해요.

## 선택지

후보는 Maven Central 메타데이터·POM과 GitHub 저장소 페이지로 확인했어요 (2026-11-27, 아래 '근거').

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. zerodep-web-push-java 2.1.5** (`com.zerodeplibs`, MIT) | 런타임 필수 의존성이 없음(HTTP 클라이언트 연동 4개는 모두 `optional`). 암호는 JDK JCA(SunEC·SunJCE)만 씀. VAPID JWT를 외부 JWT 라이브러리 없이 만듦. RFC 8291 `aes128gcm`, TTL·Urgency·Topic 지원. JDK `HttpClient`용 `HttpRequest.Builder`를 주거나, 낮은 단계 API(`VAPIDKeyPair#generateAuthorizationHeaderValue`, `MessageEncryption#encrypt`)만 써서 지금의 `HttpClient` 설정(리다이렉트 끔·시간 제한)을 그대로 쓸 수 있음. Java 11 바이트코드, `module-info` 있음. 2025-11~12에 2.1.3·2.1.4·2.1.5 배포, 저장소의 마지막 커밋은 2026-10-04(의존성 갱신 PR 병합) | 관리자 한 명, 별 11개로 사용자가 적음. 이슈 6·PR 7이 열려 있음. 2.1.5 뒤 새 배포는 없음 | 낮음: 발송 클래스 하나와 의존성 한 줄. 테스트는 결과(헤더·암호문)를 JDK로 따로 검증하니 바꿔도 그대로 씀 |
| **B. Tink apps-webpush 1.15.0** (`com.google.crypto.tink`, Apache-2.0) | Google Tink 팀이 관리함. 1.15.0이 2026-08-18 배포, 저장소 커밋 2026-11-10. 암호화 구현의 신뢰도가 높음 | RFC 8291 암호화만 함. VAPID JWT(ES256 서명·인코딩)와 헤더를 우리가 직접 만들어야 함. `tink` 본체를 거쳐 `protobuf-java`·`gson`이 함께 들어옴(apps-webpush 1.15.0은 tink 1.23.0을 씀. 의존성은 tink 1.24.0 POM으로 본 것이라 1.23.0도 같다는 것은 추측) | 낮음~중간: VAPID 코드를 우리가 들고 있음 |
| **C. nl.martijndwars web-push 5.1.2** (web-push-libs/webpush-java, MIT) | 가장 널리 쓰임(별 358). 5.1.2가 2025-02-17 배포 | 마지막 커밋 2025-07-06, 열린 이슈 42. BouncyCastle(`bcprov-jdk15on` 1.70, 옛 계열)을 보안 공급자로 등록해야 함. 보내기는 자체 Apache `httpasyncclient` 4.1.5로 하고 `jose4j`·`async-http-client`·`jcommander`(명령줄 도구용)가 런타임으로 들어옴. 우리 `HttpClient` 설정을 쓰려면 Apache 클라이언트를 따로 맞춰야 함. Java 8 기준(README) | 중간: 의존성이 많고 보안 공급자를 전역으로 등록함 |
| **D. JDK로 직접 구현** | 의존성이 전혀 없음. 우리 코드라 Java·Boot 업그레이드에 막히지 않음. 필요한 알고리즘(EC P-256, ECDH, HMAC-SHA-256, AES-GCM, `SHA256withECDSAinP1363Format`)이 모두 JDK에 있음 | 암호 코드(약 200줄, 추정)를 우리가 유지보수함. 잘못 만들면 브라우저가 조용히 버려서 실제 기기 확인 전까지 모를 수 있음. RFC 8291 부록 A 테스트 벡터, 테스트 쪽 복호화, 실제 기기 확인으로 막아야 함 | 낮음: 나중에 라이브러리로 바꾸기 쉬움 |

검토했지만 표에서 뺀 것:

- **dev.blanke webpush 6.1.3:** 저장소가 2024-10-30에 보관(archived)됐고, README가 더는 유지보수하지 않는다고 밝혀요.
- **com.interaso webpush (Kotlin):** Kotlin 표준 라이브러리가 들어오고, 마지막 배포가 2024년 초로 보여요(추측: 검색 결과의 의존성 버전으로 짐작했고 배포 날짜는 확인하지 못했어요).

## 추천과 이유

**A. zerodep-web-push-java 2.1.5**를 추천해요. 단, **낮은 단계 API만 쓰고 HTTP 요청은 우리가 직접 보내요.**

1. **의존성이 없어 Boot 4·Jackson 3과 부딪힐 일이 없어요.** 런타임 필수 의존성이 없고 암호는 JDK 공급자만 써요. C처럼 BouncyCastle을 전역 공급자로 등록하거나, B처럼 protobuf를 들이지 않아요.
2. **보내는 요청을 우리가 쥐어요.** VAPID 헤더 값과 암호문만 라이브러리에서 받고, 요청은 GitHub 클라이언트와 같은 설정의 JDK `HttpClient`(리다이렉트 끔, 연결·응답 시간 제한)로 보내요. notification → github 의존은 4.2 그래프에 없으니 클라이언트는 notification 안에 따로 만들어요. 9.7의 응답 크기 제한(응답 본문을 읽지 않고 버림)과 6장 ⑥의 전용 스레드 풀은 GitHub 클라이언트에도 없어서 S13에서 따로 정해요(S13 PR 판단 기록). 201·404·410·429·5xx도 우리가 직접 봐요.

관리자가 한 명이라는 위험은 이렇게 줄여요.

- 라이브러리를 `notification.webpush` 안의 발송 클래스 하나에 가둬요.
- 테스트는 라이브러리를 믿지 않고 결과를 JDK로 따로 검증해요. 받은 암호문을 구독 쪽 키로 RFC 8291대로 풀어 payload와 비교하고, VAPID JWT 서명·`aud`·`exp`를 공개키로 확인해요.
- 시각은 주입된 `Clock`으로만 다뤄요 (CLAUDE.md, ArchUnit `TimeRules`). jar의 바이트코드를 보면 `VAPIDJWTParam.Builder#expiresAfter`·`#buildWithDefault`(그리고 이것을 쓰는 `PreparerBuilder#vapidJWTExpiresAfter`)만 `Instant.now()`를 불러요. 그래서 JWT 만료는 `expirationTime(Clock으로 센 시각)`과 `build()`로 만들어요. 그러면 고정 시계로 `exp`를 검증할 수 있어요.

이렇게 해 두면 나중에 D(직접 구현)나 B로 바꿔도 테스트는 그대로예요.

다른 선택지를 고르면 달라지는 것:

- **B:** VAPID JWT 코드(약 50줄, 추정)를 우리가 쓰고, protobuf·gson 의존성이 더해져요.
- **C:** BouncyCastle을 등록하고 Apache 클라이언트의 시간 제한·리다이렉트를 따로 설정해요. 의존성이 다섯 개 늘어요.
- **D:** 암호 코드 약 200줄(추정)과 RFC 테스트 벡터 테스트를 S13에서 함께 만들어요. 의존성 추가는 없어요.

## 근거

- RFC 8292 VAPID: https://www.rfc-editor.org/rfc/rfc8292
- RFC 8291 Message Encryption for Web Push: https://www.rfc-editor.org/rfc/rfc8291
- RFC 8188 `aes128gcm`: https://www.rfc-editor.org/rfc/rfc8188
- RFC 8030 Web Push (TTL·Urgency·Topic, 상태 코드): https://www.rfc-editor.org/rfc/rfc8030
- A: https://github.com/st-user/zerodep-web-push-java (README: v2.x는 Java 11 이상, 의존성 없는 VAPID JWT, JCA 알고리즘 목록, MIT)
    - Maven Central `com.zerodeplibs:zerodep-web-push-java` 2.1.5 POM: 런타임 의존성 `okhttp`·`httpclient5`·`jetty-client`·`vertx-web-client` 모두 `optional`, 나머지는 `test` 범위
    - 배포 시각: 2.1.3 2025-11-24, 2.1.4 2025-11-26, 2.1.5 2025-12-31 (저장소 디렉터리 시각)
    - jar의 `javap` 결과: 공개 API `VAPIDKeyPairs.of(PrivateKeySource, PublicKeySource)`, `VAPIDKeyPair#generateAuthorizationHeaderValue(VAPIDJWTParam)`, `MessageEncryptions.of()#encrypt(UserAgentMessageEncryptionKeyInfo, PushMessage)` → `EncryptedPushMessage#toBytes()·contentEncoding()`, `StandardHttpClientRequestPreparer#toRequestBuilder()`
    - jar 매니페스트 `Java-Version: 11`, `module-info` 있음
    - jar 바이트코드(`javap -c`)에서 시스템 시각을 읽는 곳: `VAPIDJWTParam$Builder`의 `expiresAfter`·`buildWithDefault`(내부 `now()` → `Instant.now()`)뿐
    - GitHub 페이지 (2026-11-27): 별 11, 마지막 커밋 2026-10-04, 열린 이슈 6·PR 7, 보관 안 됨
- B: https://github.com/tink-crypto/tink-java-apps (Apache-2.0, 1.15.0 2026-08-18(저장소 디렉터리 시각, 메타데이터 `lastUpdated`는 2026-08-19), 마지막 커밋 2026-11-10). POM: `tink` 1.23.0. `tink` 1.24.0 POM의 필수 의존성은 `protobuf-java`, `gson`, `jsr305`, `error_prone_annotations`
- C: https://github.com/web-push-libs/webpush-java (MIT, README "A Web Push library for Java 8", 5.1.2 2025-02-17, 마지막 커밋 2025-07-06, 열린 이슈 42·PR 17). 5.1.2 POM: `httpasyncclient` 4.1.5, `async-http-client` 2.12.4, `jose4j` 0.7.9, `jcommander` 1.81(런타임), `bcprov-jdk15on` 1.70(optional, README는 등록하라고 안내)
- 뺀 것: https://github.com/andreblanke/webpush-java (2024-10-30 보관)
- **확인하지 못한 것:**
    - A가 Java 25에서 실제로 요청을 만들고, 그 결과가 JDK 복호화·서명 검증을 통과하는지 아직 돌려 보지 못했어요. 받은 jar를 빌드 밖에서 따로 실행하는 실험은 이 세션의 권한 설정이 막았어요. 고르면 S13 구현 PR의 테스트가 Gradle 의존성으로 처음 확인해요(아래 '검증 방법').
    - 실제 브라우저(Chrome·Firefox·Safari)에서 받는지는 H-04와 S16 뒤에 사람이 확인해요.

## 검증 방법

- **자동 테스트 (S13):** WireMock으로 푸시 서비스를 흉내 내고, 받은 요청을 확인해요.
    - 본문을 테스트에서 만든 구독 키(`p256dh` 개인키, `auth`)로 RFC 8291대로 JDK만 써서 풀면 보낸 payload와 같아요
    - `Content-Encoding: aes128gcm`
    - `Authorization`의 JWT가 VAPID 공개키로 검증되고, `aud`가 구독 주소의 출처이고, `exp`가 24시간 안이고, `k`가 우리 공개키예요
    - `TTL`·`Urgency`·`Topic` 값
    - 상태 코드별 처리: 201·404·410·429·5xx
- **실제 기기:** H-04(운영 VAPID 키)와 S16(서비스 워커) 뒤에 iPhone(홈 화면 PWA)·Android·데스크톱 브라우저에서 받는지 확인해요 (infra 9.10 '웹 푸시 수신 확인').
- **운영:** 발송 성공률(SENT ÷ (SENT + FAILED))과 410 정리 건수 지표(5.8, metrics.md 10.2). 성공률이 갑자기 떨어지면 암호화·VAPID 문제를 먼저 의심해요.
- **다시 열 조건:** 이 라이브러리가 Java·Boot 업그레이드를 막거나, 보안 문제가 나왔는데 고쳐지지 않거나, 푸시 서비스가 요구하는 형식이 바뀌면 D(직접 구현)나 B로 바꿔요.

<!-- 여기부터 사람이 정해요. 사람이 요청하면 에이전트가 받아 적고, '결정' 절 맨 위에 그렇게 표시해요 (docs/workflow.md 3장). 요청이 없으면 에이전트는 비워 둬요 -->

## 결정

> 사람이 정했고 요청에 따라 에이전트가 받아 적었다 (2026-11-27, S13 세션 선택지 "A. zerodep 2.1.5 (Recommended)". 세션 시작 지시 "S13(알림 기반과 웹 푸시)을 시작", 같은 세션의 앞선 선택지 "Proposal first, decide in-session (Recommended)". 그 선택지 설명이 '사람이 세션에서 고르고 에이전트가 docs PR에 결정을 받아 적는다'여서 받아 적기 요청으로 봤어요).

**A. zerodep-web-push-java 2.1.5**를 써요. 라이브러리에서는 VAPID 헤더 값과 암호문만 받고, 요청은 지금의 JDK `HttpClient`(리다이렉트 끔, 시간 제한)로 우리가 보내요 (추천안 그대로).

포기한 것: B(Tink apps-webpush), C(nl.martijndwars web-push), D(JDK로 직접 구현).

## 검토 메모

(사람이 따로 말하지 않아 비워 둬요.)
