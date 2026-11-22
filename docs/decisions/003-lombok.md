# 003. Lombok 사용 여부와 범위

| 항목 | 내용 |
|---|---|
| 상태 | 제안 |
| 관련 | OD-05, S04·S05·S06 (엔티티를 처음 만드는 단계), `docs/spec/architecture.md` 기술 스택 'Lombok' 행 |
| 제안 | 에이전트, PR #. OD-05의 '준비'는 결정 대장상 사람이지만, 2026-11-22 사람의 요청으로 에이전트가 초안을 썼어요 |
| 날짜 | 2026-11-22 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

엔티티를 처음 만드는 단계(S04·S05·S06 중 먼저 하는 것) 전에 Lombok을 쓸지, 쓴다면 어디까지 쓸지 정해야 해요 (결정 대장 OD-05).

**지금 코드**

- main 코드에 Lombok이 없어요. 값 객체와 결과 타입은 Java record로 만들었어요 (`StudyTimeRule`, `ProblemRef`, `ParsedCommit`, `GlobalExceptionHandler.InvalidField` 등).
- 엔티티와 저장소는 아직 없어요. S04의 `users`가 첫 엔티티가 될 가능성이 커요.
- 생성자 주입을 쓰는 Spring 빈은 아직 거의 없어요 (`ClockConfiguration` 정도).

**제약**

- 기술 스택은 Java 25, Spring Boot 4.1, Hibernate 7이에요 (D2).
- 엔티티는 모듈의 하위 패키지에 둬요 (결정 001, ArchUnit이 검사).

**사람이 이번 세션에서 답한 기준 (2026-11-22):** 읽기 쉬움. PR을 검토할 때 getter·생성자 같은 반복 코드가 적은 쪽에 무게를 둬요.

**버전 호환:** Spring Boot 4.1.3이 Lombok 1.18.48을 관리해요. JDK 25 지원은 1.18.40부터라 Boot가 관리하는 버전으로 충분해요. 따로 버전을 적을 필요가 없어요.

## 선택지

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| A. 쓰지 않음 (record + 직접 작성) | 컴파일러에 끼어드는 도구가 없어요. 새 JDK가 나와도 Lombok 지원을 기다릴 일이 없어요. 지금 코드와 같은 방식이에요 | 엔티티마다 getter, 보호된 기본 생성자, 서비스마다 생성자를 직접 써요. 기준(읽기 쉬움)에서 반복 코드가 가장 많아요 | 낮음: 나중에 B로 넓히기는 쉬워요 |
| **B. 범위를 정해 허용** (아래 허용·금지 목록) | 반복 코드가 가장 많은 곳(엔티티 getter·기본 생성자, 생성자 주입)만 줄여요. 엔티티의 equals·hashCode·toString처럼 Hibernate와 부딪히는 기능은 막아요. 값 객체·DTO는 지금처럼 record로 둬서 Lombok이 필요 없어요 | 무엇이 허용인지 규칙을 알아야 해요. 일부 규칙은 도구로 못 막고 리뷰로 봐야 해요 (아래 '강제 방법'). Lombok이 새 JDK를 지원할 때까지 JDK를 올릴 수 없어요 | 낮음~중간: 허용한 애너테이션만큼 delombok이나 직접 작성으로 되돌려야 해요 |
| C. 넓게 허용 (`@Data` 등 포함) | 반복 코드가 가장 적어요 | `@Data`는 `@EqualsAndHashCode`·`@ToString`·`@Setter`를 함께 만들어요. `@EqualsAndHashCode`는 기본으로 static·transient가 아닌 모든 필드를 써서, 엔티티에 붙이면 Hibernate 문서의 권고(바뀌는 필드·DB가 만든 ID를 hashCode에 넣지 않기)와 부딪혀요. 모든 필드에 setter가 생겨 엔티티를 아무 데서나 바꿀 수 있게 돼요 | 중간: 나중에 좁히려면 이미 쓴 곳을 찾아 고쳐야 해요 |

**B의 범위안**

| 구분 | 애너테이션 | 쓰는 곳과 이유 |
|---|---|---|
| 허용 | `@Getter` | 엔티티 등. 필드 읽기 |
| 허용 | `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | 엔티티. Hibernate는 인자 없는 non-private 생성자가 필요해요. protected로 두면 다른 코드가 빈 엔티티를 만들지 못해요 |
| 허용 | `@RequiredArgsConstructor` | 서비스·컴포넌트의 생성자 주입 |
| 허용 | `@Slf4j` | 로거 필드 |
| 사람이 정할 것 | `@Builder` | 인자가 많은 엔티티·객체를 만들 때 읽기 쉬워요. 대신 필수 값 검사가 생성자 밖으로 빠지기 쉬워요. 허용한다면 '생성자에 붙이고 필수 값은 생성자에서 검사'처럼 쓰는 방식을 함께 정하는 게 좋아요 |
| 금지 | `@Data` | 위 C의 이유 |
| 금지 | `@Value` | record를 써요 (지금 코드와 같은 방식) |
| 금지 (엔티티) | `@Setter`, `@EqualsAndHashCode`, `@ToString` | 상태 변경은 이름 있는 메서드로 해요. equals·hashCode는 필요할 때 Hibernate 권고대로 자연 키로 직접 써요. toString은 지연 로딩 연관을 건드릴 수 있어요 (추측: Hibernate 문서에서 직접 확인하지는 않았어요) |
| 금지 | `@SneakyThrows`, `lombok.experimental` 전체 | 검사 예외를 숨기거나, 아직 실험 단계인 기능이에요 |

**강제 방법**

- **전역 금지는 `lombok.config`로 해요.** Lombok은 `lombok.<기능>.flagUsage = error`로 기능 사용을 컴파일 오류로 막을 수 있어요 (예: `lombok.data.flagUsage = error`, `lombok.value.flagUsage`, `lombok.sneakyThrows.flagUsage`, `lombok.experimental.flagUsage`). 레포 루트에 두고 `config.stopBubbling = true`를 적어요.
- **'엔티티에서만 금지'는 도구로 깔끔하게 막기 어려워요.** `lombok.config`는 디렉터리 단위라, 엔티티가 여러 모듈의 하위 패키지에 흩어지면 디렉터리마다 설정을 둬야 해요. 그래서 리뷰와 `spec-reviewer` 확인으로 보는 걸 제안해요.
- **ArchUnit으로는 Lombok 애너테이션을 볼 수 없어요.** 확인한 애너테이션(`@Getter`, `@Setter`, `@Data`, `@RequiredArgsConstructor`, `@NoArgsConstructor`, `@EqualsAndHashCode`, `@Slf4j`, `@Builder`)이 모두 `@Retention(SOURCE)`라 바이트코드에 남지 않아요 (Lombok 1.18.48 jar에서 확인). 대신 결과물은 검사할 수 있어요. 예를 들어 '엔티티에 public setter가 없다'는 ArchUnit 규칙은 Lombok이 만든 것과 직접 쓴 것을 모두 잡아요. 이 규칙을 둘지는 첫 엔티티 단계에서 판단 기록으로 정하는 걸 제안해요.

## 추천과 이유

**B (범위를 정해 허용)**

- 기준이 '읽기 쉬움'이라 반복 코드가 가장 많이 남는 A보다 B가 맞아요. 이 서비스에서 반복 코드가 생기는 곳은 엔티티의 getter·기본 생성자와 서비스의 생성자 주입인데, B는 정확히 그곳만 줄여요.
- C는 반복 코드가 더 적지만, `@Data`·`@EqualsAndHashCode`·`@Setter`가 엔티티에서 Hibernate 권고와 부딪히는 문제를 만들어요. 이 문제는 테스트가 바로 잡아 주지 않고, 컬렉션에 담기거나 로그를 찍을 때 늦게 드러나요 (추측: 경험칙이고 직접 재현하지는 않았어요).
- 값 객체·DTO는 지금처럼 record로 둬요. record가 생성자·접근자·equals·hashCode·toString을 이미 만들어 주므로, Lombok이 필요한 곳은 엔티티와 Spring 빈으로 좁혀져요.

**사람이 정할 것:** `@Builder` 허용 여부.

**B를 받아들이면 바뀌는 것**

| 문서·파일 | 바뀌는 곳 | 누가 |
|---|---|---|
| `docs/spec/architecture.md` 기술 스택 표 'Lombok' 행 | "미정, OD-05" → 허용 범위 요약과 결정 제안서 003 | 사람 |
| `build.gradle` | `compileOnly`·`annotationProcessor`(+ test) 추가. 버전은 Boot BOM | 첫 엔티티 단계의 에이전트 |
| `lombok.config` (레포 루트) | 금지 기능의 `flagUsage = error`, `config.stopBubbling = true` | 첫 엔티티 단계의 에이전트 |

**다른 선택지를 고르면 바뀌는 것**

- A: 위 표에서 build.gradle·`lombok.config` 변경이 없어요. 엔티티 getter·생성자와 서비스 생성자를 직접 써요.
- C: `lombok.config`의 금지 항목이 줄어들고, 엔티티 equals·hashCode 규칙을 리뷰로만 지켜야 해요.

## 근거

모두 2026-11-22에 확인했어요.

- [spring-boot-dependencies 4.1.3 POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.3/spring-boot-dependencies-4.1.3.pom): `lombok.version` 1.18.48.
- [Lombok changelog](https://projectlombok.org/changelog): v1.18.40(2025-09-04) "JDK25 support added", v1.18.48(2026-09-01) "JDK27 support added".
- [Lombok — Gradle 설정](https://projectlombok.org/setup/gradle): `compileOnly`, `annotationProcessor`, `testCompileOnly`, `testAnnotationProcessor`.
- [Lombok — @EqualsAndHashCode](https://projectlombok.org/features/EqualsAndHashCode): "By default, it'll use all non-static, non-transient fields".
- [Lombok — @Data](https://projectlombok.org/features/Data): `@ToString`, `@EqualsAndHashCode`, 모든 필드의 `@Getter`, final이 아닌 필드의 `@Setter`, `@RequiredArgsConstructor`를 묶은 것.
- [Lombok — Configuration system](https://projectlombok.org/features/configuration): `lombok.(featureName).flagUsage`(warning/error), 디렉터리 계층, `config.stopBubbling`.
- Lombok 1.18.48 jar(Maven Central): 위 애너테이션들이 `@Retention(SOURCE)` (`javap`로 확인).
- [Hibernate ORM 7.4 Introduction](https://docs.hibernate.org/orm/7.4/introduction/html_single/): 3.1 엔티티는 final이 아니고 인자 없는 non-private 생성자 필요. 3.26 hashCode에 바뀌는 필드를 넣지 말고, DB가 만든 ID도 넣지 말며, 자연 키를 쓰라.

**확인하지 못한 것 (추측)**

- 엔티티의 Lombok `@ToString`이 지연 로딩 연관을 건드리는 문제 (Hibernate 문서에서 직접 확인하지 않았고, 경험칙이에요)
- `@EqualsAndHashCode` 엔티티 문제가 늦게 드러난다는 점 (직접 재현하지 않았어요)
- `lombok.config`를 디렉터리마다 둬서 엔티티 패키지에만 금지를 거는 방식이 이 레포 구조에서 실용적인지

## 검증 방법

- **첫 엔티티 단계:** Lombok을 더한 뒤 `./gradlew build`(ArchUnit 포함)가 통과하는지, `lombok.config`의 금지 기능을 일부러 써서 컴파일이 실패하는지 확인하고 PR에 적어요.
- **엔티티 규칙:** 첫 엔티티 PR에서 '엔티티에 public setter 없음' 같은 ArchUnit 규칙을 둘지 판단 기록으로 정해요.
- **JDK 업그레이드:** JDK를 올릴 때 Boot가 관리하는 Lombok 버전이 그 JDK를 지원하는지 changelog로 확인해요. 지원이 늦어지면 이 결정을 다시 열 조건이에요.

<!-- 여기부터 사람이 써요. 에이전트는 비워 둬요 -->

## 결정

## 검토 메모
