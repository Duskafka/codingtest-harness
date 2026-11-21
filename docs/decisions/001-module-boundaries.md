# 001. 모듈 공개 API의 패키지 표현과 의존 규칙 해석

| 항목 | 내용 |
|---|---|
| 상태 | 제안 |
| 관련 | S01, `docs/spec/architecture.md` 4.2, 알려진 충돌과 빈 곳 1번(3번은 S15로 미룸) |
| 제안 | 에이전트, PR #1 |
| 날짜 | 2026-11-21 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

4.2는 모듈 사이의 규칙을 세 가지로 정해요.

- 화살표 방향으로만 의존한다.
- 다른 모듈의 엔티티를 직접 수정하지 않고, 공개된 서비스 메서드로만 접근한다.
- 이 규칙을 ArchUnit으로 CI에서 강제한다.

S01은 이 규칙을 ArchUnit 테스트로 만들어야 하는데, 세 가지가 정해져 있지 않아요.

1. **공개 API를 패키지로 어떻게 표현하나.** ArchUnit이 '공개된 서비스'와 '엔티티·저장소'를 구분하려면 패키지 규칙이 있어야 해요. Java의 접근 제한자로는 하위 패키지를 넘어 막을 수 없어요.
2. **'화살표 방향으로만'을 어디까지 허용하나.** 예를 들어 `github → verification → progress`일 때 `github`가 `progress`를 직접 써도 되는지(전이 허용) 정해져 있지 않아요.
3. **`auth`와 `common`의 관계 (알려진 충돌 1번).** 4.2 그래프에 둘 다 없어요. 대부분의 모듈이 '현재 사용자'를 알아야 하고, `common`(Clock, StudyCalendar, 오류 형식, 보안 유틸)은 모두가 써요.

제약

- 모듈러 모놀리스이고 혼자 개발한다 (D3).
- 기술 스택 표는 구조 검사 도구로 ArchUnit을 정해 두었다. 새 라이브러리를 들이는 것은 큰 판단이다.
- 계정 삭제(5.16)가 여러 모듈의 데이터를 지우는 방향(알려진 충돌 3번)은 S15에서 결정 제안서로 다룬다. 여기서는 정하지 않는다.

## 선택지

### ① 공개 API 표현

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. 모듈 루트 패키지 = 공개, 하위 패키지 = 내부** (Spring Modulith 기본 관례) | 규칙이 하나라 단순하다. 패키지 이름을 자유롭게 지을 수 있다(`study.persistence`, `study.web` 등). 나중에 Spring Modulith를 들여도 구조를 바꾸지 않아도 된다 | 루트에 둔 클래스는 모두 공개라서, 공개 범위를 넓히지 않으려면 신경 써야 한다. 꼭 필요한 하위 패키지(예: `verification.source`)는 허용 목록으로 따로 공개해야 한다 | 낮음 (아직 클래스가 없음) |
| B. `<모듈>.api` = 공개, 나머지 = 내부 | 무엇이 공개인지 이름에서 바로 보인다 | 모든 모듈에 `api` 패키지가 생기고 루트 패키지는 비게 된다. 4.2 트리의 `verification/source/`와 모양이 다르다 | 낮음 |
| C. 애너테이션(`@PublicApi` 등)으로 표시 | 패키지와 무관하게 정할 수 있다 | 붙이는 걸 잊으면 규칙이 거꾸로 작동하거나 빠진다. 애너테이션을 새로 만들어야 한다 | 중간 |
| D. Spring Modulith 도입 | 모듈 검증, 모듈 문서, 이벤트 발행 기록(커밋 뒤 이벤트를 DB에 남김)을 함께 얻는다 | 새 라이브러리와 관례를 지금 들인다. 기술 스택은 ArchUnit을 정해 두었다. Boot 4.1을 지원하는 버전 라인을 공식 문서에서 확인하지 못했다 | 중간 |

### ② 화살표 해석

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. 직접 그려진 화살표만 허용** | 스펙의 그래프가 그대로 계약이 된다. 새 의존이 필요하면 허용 목록 한 줄과 스펙 변경 제안으로 드러난다 | 단계 구현 중에 '화살표 하나 더'가 필요해질 수 있다 (예: verification이 study를 직접 봐야 할 때) | 낮음: 나중에 넓히기는 쉽다 |
| B. 전이 허용 (화살표를 따라 닿는 모듈은 모두 허용) | 덜 막힌다 | 결합이 넓게 퍼진다. 나중에 좁히려면 이미 생긴 의존을 걷어내야 한다 | 높음: 나중에 좁히기는 어렵다 |

### ③ auth·common

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. `common`: 어느 모듈에도 의존하지 않고 모두가 쓴다 (하위 패키지까지 공개). `auth`: `common`만 의존하고, 모든 모듈이 `auth`의 공개 API를 쓴다** | 4.2의 `common` 설명(모두가 쓰는 도구)과 맞다. 현재 사용자·사용자 표시 정보 같은 것을 각 모듈이 바로 쓸 수 있다 | `auth`의 공개 API가 넓어지면 모든 모듈이 거기에 묶인다. 계정 삭제처럼 `auth`가 다른 모듈을 불러야 하는 일은 이 규칙으로는 안 되고, S15에서 따로 정해야 한다 | 낮음 |
| B. 현재 사용자 타입(예: 사용자 ID)만 `common`에 두고, 모듈은 `auth`에 의존하지 않는다 | `auth`와의 결합이 가장 작다 | 멤버 이름·아바타처럼 사용자 정보가 필요한 화면(오늘 현황 등)에서 결국 `auth`가 필요해진다 | 낮음 |
| C. 필요한 모듈만 하나씩 `auth` 화살표를 더한다 | 가장 엄격하다 | 단계마다 같은 판단을 반복하게 된다 | 낮음 |

## 추천과 이유

**① A, ② A, ③ A를 추천해요.**

- **① A:** 규칙 하나로 '다른 모듈의 엔티티·저장소를 직접 쓰지 않는다'를 강제할 수 있어요. 엔티티와 저장소를 하위 패키지에 두도록 함께 검사하기 때문이에요. 또 Spring Modulith의 기본 관례와 같아서, 나중에 Modulith를 들여도(예: 알려진 충돌 2번의 이벤트 유실 문제를 이벤트 발행 기록으로 풀 때) 패키지를 옮길 필요가 없어요. 지금 Modulith를 바로 들이지 않는 이유는 기술 스택이 ArchUnit을 정해 두었고, Boot 4.1 지원 버전을 확인하지 못했기 때문이에요.
- **② A:** 넓히는 쪽은 언제든 싸고 좁히는 쪽은 비싸요. 직접 화살표만 허용하면 새 의존이 필요할 때마다 PR에 드러나서 사람이 검토할 수 있어요.
- **③ A:** 대부분의 모듈이 현재 사용자와 사용자 표시 정보를 써야 해서, `auth`를 모두에게 열되 `auth`는 `common` 말고는 의존하지 않게 막는 게 가장 단순해요. 계정 삭제가 다른 모듈을 부르는 방향은 이 규칙에 걸리므로, S15에서 이벤트 등으로 풀어야 한다는 게 자연스럽게 드러나요.

다른 선택지를 고르면 바뀌는 것은 `src/test/java/com/duskafka/studybot/architecture/ModuleRules.java`의 허용 목록과 패키지 판정 함수예요. main 코드는 아직 `package-info.java`뿐이라 옮길 클래스가 없어요.

**이 추천으로 구현한 규칙** (`ModuleRules`)

| 규칙 | 내용 |
|---|---|
| 의존 방향 | 4.2의 직접 화살표 + 모든 모듈 → `common`, `auth`. `common`은 다른 모듈에, `auth`는 `common` 말고는 의존하지 않음 |
| 순환 금지 | 모듈 사이 순환 의존 금지 (허용 목록이 순환을 만들지 않도록 한 번 더) |
| 내부 접근 금지 | 다른 모듈의 하위 패키지에 의존하지 않음. `common`은 전부 공개. 공개 하위 패키지 허용 목록은 비어 있음 (`verification.source`를 공개할지는 S08에서 판단) |
| 엔티티·저장소 위치 | `@Entity`와 Spring Data `Repository`는 모듈 루트 패키지에 둘 수 없음 |
| 패키지 범위 | main 클래스는 루트 패키지(애플리케이션 클래스)나 4.2의 모듈 패키지에만 있음 |

**허용 목록**

| 모듈 | 의존해도 되는 모듈 |
|---|---|
| common | 없음 |
| auth | common |
| study | common, auth |
| github | verification, common, auth |
| verification | progress, common, auth |
| progress | study, common, auth |
| judgment | progress, ledger, common, auth |
| ledger | common, auth |
| appeal | judgment, common, auth |
| notification | progress, study, common, auth |
| discord | progress, notification, common, auth |

## 근거

- `docs/spec/architecture.md` 4.2 (모듈 구조와 규칙), 알려진 충돌과 빈 곳 1번·2번·3번
- Spring Modulith 레퍼런스 2.2.0, Fundamentals: https://docs.spring.io/spring-modulith/reference/fundamentals.html
    - "By default, each direct sub-package of the main package is considered an application module package."
    - "an application module's base package is considered the API package and thus is the only package to allow incoming dependencies from other modules."
    - 하위 패키지를 공개하려면 `@NamedInterface`를 쓰고, 허용 의존은 `@ApplicationModule(allowedDependencies = ...)`로 적어요. 이 PR의 `EXPOSED_SUBPACKAGES`와 `ALLOWED_DEPENDENCIES`가 같은 역할이에요.
- ArchUnit 1.5.1 사용자 가이드 (https://www.archunit.org/userguide/html/000_Index.html): 빈 클래스 집합에 대한 규칙은 기본적으로 실패해요(`failOnEmptyShould`). 모듈이 아직 비어 있어서 모듈 규칙에는 `allowEmptyShould(true)`를 쓰고, 규칙이 실제로 걸리는지는 예제로 따로 검증해요.
- **추측:** Spring Modulith의 Boot 4.1 지원 버전. 레퍼런스의 호환 표는 "2.0 (snapshot) / 4.0 SNAPSHOT"에서 멈춰 있어서 확인하지 못했어요. D안을 다시 볼 때 GitHub 릴리스 노트로 확인해야 해요.

## 검증 방법

- `ArchitectureTest`: main 코드에 위 규칙을 적용해요 (CI의 `./gradlew build`에 포함).
- `ArchitectureRulesSelfTest`: 테스트 소스의 `com.duskafka.archfixtures` 아래에 규칙을 어기는 예제와 지키는 예제를 두고, 같은 규칙이 각각 걸리는지, 통과하는지 확인해요.
    - 걸려야 하는 예제: study → github, common → auth, 다른 모듈의 내부 패키지 접근, 루트 패키지의 엔티티·저장소, 순환, 모듈 밖 최상위 패키지
    - 통과해야 하는 예제: 허용된 방향, common 하위 패키지, auth 공개 API, 같은 모듈 안의 내부 패키지
- 이후 단계에서 허용 목록에 없는 의존이 필요해지면, 그 단계 PR의 '스펙 변경 제안'으로 드러나요. 그 빈도로 ② A가 지나치게 엄격한지 판단할 수 있어요.

<!-- 여기부터 사람이 써요. 에이전트는 비워 둬요 -->

## 결정

## 검토 메모
