# S01. 공통 기반

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S00 (첫 커밋) |
| 결정 게이트 | 없음 |
| 사람 할 일 | H-00 (master 보호), H-09 (JDK 25 설정 스크립트) |
| T번호 | 없음 (구조·보안 테스트) |

## 목표

이후 모든 단계가 기대는 뼈대를 만든다: 모듈 패키지와 의존 규칙 강제, 시간 주입, 공통 오류 형식, API 명세, 헬스체크, 클라우드 세션 준비 스크립트.

## 읽을 문서

- `docs/spec/architecture.md`: 4.2 전체, '기술 스택', '알려진 충돌과 빈 곳' 1번·8번
- `docs/spec/api.md`: 4.4 표 아래의 오류 형식·명세·페이지네이션 항목
- `docs/spec/security.md`: 9.7 '운영 엔드포인트와 오류'
- `docs/spec/testing.md`: 하네스 규칙, 8.1의 '구조' 행
- `docs/workflow.md`: 8장 환경 준비

## 만들 것

1. **모듈 패키지:** 4.2의 모듈마다 `com.duskafka.studybot.<모듈>` 패키지와 한 줄 설명(`package-info.java`)을 만든다.
2. **의존 규칙 테스트 (ArchUnit):**
    - 4.2 그래프의 화살표 방향으로만 의존한다.
    - 다른 모듈의 엔티티·저장소를 직접 쓰지 않고 공개된 서비스로만 접근한다. 이걸 패키지 구조로 어떻게 표현할지(예: 모듈 안 공개·내부 패키지 구분)는 결정 제안서로 쓴다.
    - 4.2에 없는 `auth`·`common`의 관계 해석도 같은 결정 제안서에 쓴다 (알려진 충돌 1번).
    - 규칙이 비어 있어서 통과하는 게 아님을 보이는 테스트를 둔다 (규칙을 어기는 테스트용 예제가 실제로 걸리는지).
3. **시간 주입:** `Clock` 빈을 하나 두고, main 코드에서 `now()` 직접 호출(`LocalDate.now()`, `Instant.now()`, `ZonedDateTime.now()` 등)과 `System.currentTimeMillis()`를 ArchUnit으로 막는다. `Clock` 설정 클래스만 예외다.
4. **테스트용 시계:** 통합 테스트가 현재 시각을 정하고 옮길 수 있게 한다. 이후 판정·알림 테스트가 이걸 쓴다.
5. **공통 오류 형식:** RFC 9457 `application/problem+json` (`ProblemDetail`). 검증 실패 400, 인증 401, 권한 403, 없음 404, 충돌 409, 처리 못 한 예외 500. 스택 트레이스·예외 클래스 이름·쿼리를 응답에 넣지 않는다.
6. **API 명세:** springdoc-openapi(Boot 4 지원 계열)로 명세를 생성한다. 운영에서 노출할지는 판단 기록으로 남긴다 (알려진 충돌 8번).
7. **Actuator:** `health`만 노출한다. `env`, `heapdump`, `configprops` 등은 노출하지 않는다.
8. **클라우드 세션 준비 스크립트:** `scripts/cloud-session-setup.sh`. `CLAUDE_CODE_REMOTE`가 `true`일 때만 동작하고, Docker 데몬이 없으면 띄운다. 여러 번 실행해도 안전해야 한다. SessionStart 훅이 이 파일을 실행한다 (`.claude/hooks/session-start.sh`).

## 만들지 않을 것

- 엔티티, 테이블, Flyway 마이그레이션 (OD-05, OD-06 뒤)
- Spring Security 설정 (S04)
- 스케줄러와 ShedLock (S12)

## 수용 기준

- [ ] 4.2에 없는 방향으로 의존하는 테스트용 예제가 ArchUnit 규칙에 걸린다
- [ ] main 코드에 `now()` 직접 호출이 있으면 ArchUnit 테스트가 실패한다 (`Clock` 설정 클래스는 통과)
- [ ] 없는 경로 요청 → 404 `application/problem+json`, 본문에 스택 트레이스와 예외 클래스 이름이 없다
- [ ] 요청 본문 검증 실패 → 400 `application/problem+json`, 어느 필드가 왜 틀렸는지 담긴다
- [ ] 처리하지 못한 예외 → 500 `application/problem+json`, 내부 정보가 없다
- [ ] `/actuator/health`는 200, `/actuator/env`와 `/actuator/heapdump`는 노출되지 않는다
- [ ] OpenAPI 명세가 생성된다
- [ ] 통합 테스트에서 시각을 정하고 옮기는 예제 테스트가 있다
- [ ] `scripts/cloud-session-setup.sh`를 두 번 연달아 실행해도 문제가 없다 (PR 실행 기록에 결과)

## 주의할 것

- ArchUnit이 Java 25 클래스 파일을 읽을 수 있는 버전인지 확인한다.
- Boot 4의 ProblemDetail·Actuator 설정 속성 이름을 공식 문서로 확인한다. 3.x 블로그의 속성 이름을 그대로 쓰지 않는다.
- 첫 세션이 JDK 25를 설치하고 Docker를 준비한 방법은 첫 세션 보고에 있고, 레포에는 남아 있지 않다. Docker 데몬을 띄우는 방법은 이 단계에서 다시 확인하고 근거를 남긴다. JDK 설치는 환경 설정 스크립트(H-09)의 몫이라 이 스크립트에 넣지 않는다.

## 판단 기록이 필요한 곳

- **결정 제안서:** 모듈 공개 API를 패키지로 표현하는 방식, `auth`·`common` 의존 해석 (구현과 함께 PR에)
- **판단 기록:** springdoc 노출 범위, `Clock`의 기본 시간대, 테스트용 시계 방식
