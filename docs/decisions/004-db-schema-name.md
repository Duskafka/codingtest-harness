# 004. DB 스키마 이름

| 항목 | 내용 |
|---|---|
| 상태 | 제안 |
| 관련 | OD-06, S04·S05·S06 (첫 마이그레이션을 만드는 단계), `docs/spec/infra.md` 9.4, `docs/spec/data-model.md` 머리 안내, D11, D23 |
| 제안 | 에이전트, PR #. OD-06의 '준비'는 결정 대장상 사람이지만, 2026-11-22 사람의 요청으로 에이전트가 초안을 썼어요 |
| 날짜 | 2026-11-22 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

첫 Flyway 마이그레이션 전에 앱 테이블을 둘 스키마 이름을 정해야 해요 (결정 대장 OD-06, data-model.md 머리 안내).

**스펙의 요구:** infra.md 9.4는 Supabase에서 "Data API 끄기 + 앱 테이블은 `public`이 아닌 별도 스키마"를 쓰라고 해요. 9.10 운영 시작 전 점검표에도 "Data API 꺼짐"이 있어요. 이름은 정해져 있지 않아요.

**왜 `public`이 아닌가 (Supabase 문서):** Supabase는 `public` 스키마를 Data API(자동 REST·GraphQL 엔드포인트)에 기본으로 노출해요. 별도 스키마는 'Exposed schemas'에 넣고 권한을 줘야 노출돼요. Data API를 끄면 권한과 상관없이 어떤 자동 엔드포인트도 응답하지 않아요. 그래서 9.4의 두 가지는 서로를 받쳐 주는 이중 방어예요. 이 서비스는 Spring만 DB에 접근하므로(D11) Data API가 필요 없어요.

**제약**

- Supabase가 이미 쓰는 스키마 이름과 겹치면 안 돼요. 예를 들어 Supabase Auth는 `auth` 스키마(`auth.users`)를 써요.
- 9.4는 DB 역할을 셋으로 나눠요: 마이그레이션용(소유자), 앱 실행용(조회·쓰기), 백업용(읽기). 원장 테이블은 앱 역할에서 UPDATE·DELETE를 회수해요.
- 테스트는 Testcontainers의 `postgres:17`로 돌아요. 운영(Supabase)과 같은 스키마 이름으로 맞춰야 마이그레이션이 같은 결과를 내요.

**사람이 이번 세션에서 고른 이름 (2026-11-22):** `studybot`

## 선택지

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. `studybot` 스키마 하나** | 기본 패키지 `com.duskafka.studybot`(D23)과 같은 코드명이라 코드와 DB가 한눈에 이어져요. 같은 DB에 다른 것이 생겨도 어느 앱의 테이블인지 바로 보여요. 서비스 이름이 바뀌어도 패키지처럼 코드명으로 남길 수 있어요 | 서비스 이름이 바뀌면 코드명과 서비스 이름이 달라 보여요 (패키지도 마찬가지예요) | 중간: 이름을 바꾸려면 `ALTER SCHEMA … RENAME`과 설정·마이그레이션 기록 위치를 함께 옮겨야 해요. 데이터가 쌓인 뒤라면 운영 중 작업이에요 |
| B. `app` 스키마 하나 | 서비스 이름과 무관해서 이름이 바뀌어도 어색하지 않아요 | 무엇의 테이블인지 드러나지 않아요. 흔한 이름이라 같은 DB에 다른 도구가 같은 이름을 쓸 여지가 있어요 (추측) | A와 같음 |
| C. 모듈별 스키마 (`study`, `progress` 등) | 모듈 경계(4.2)가 DB에도 드러나요 | `auth` 모듈 이름이 Supabase의 `auth` 스키마와 겹쳐요. 모듈 사이 외래 키가 스키마를 넘나들고, 역할·권한 설정이 모듈 수만큼 늘어요. Flyway·Hibernate·Spring Session 설정도 여러 스키마를 다뤄야 해요. 혼자 운영하는 서비스 한 대(D3, D10)에 비해 비용이 커요 | 높음: 테이블이 생긴 뒤 합치거나 나누기 어려워요 |

## 추천과 이유

**A. `studybot` 스키마 하나**

- 스키마 하나로 9.4의 요구('`public`이 아닌 별도 스키마')를 채우는 데 충분해요. 모듈 경계는 이미 ArchUnit이 코드에서 지켜요 (결정 001). DB까지 나누면 비용만 늘어요.
- 이름은 사람이 이 세션에서 고른 `studybot`이에요. 기본 패키지와 같은 코드명이라 코드와 DB를 함께 읽기 쉬워요. Supabase가 쓰는 스키마 이름과도 겹치지 않을 것으로 보여요 (추측: Supabase가 만드는 스키마 목록을 문서에서 다 확인하지는 않았어요. 첫 마이그레이션 전에 대시보드에서 확인해요).

**적용 방법 (첫 마이그레이션 단계에서 판단 기록으로)**

이름만 정하면 되고, 설정 방법은 첫 마이그레이션을 만드는 단계에서 정하는 걸 제안해요. 그때 볼 것을 적어 둬요.

- **Flyway:** `spring.flyway.default-schema`, `spring.flyway.schemas`. `spring.flyway.create-schemas`는 기본이 `true`라 스키마가 없으면 만들어요. 운영에서 스키마를 마이그레이션 역할이 만들게 할지, 미리 만들어 둘지는 9.4 역할 분리와 함께 정해요.
- **JPA(Hibernate):** `spring.jpa.properties.hibernate.default_schema`로 기본 스키마를 지정하거나, 연결의 search_path로 맞춰요.
- **Spring Session JDBC:** 테이블 이름 기본값은 `SPRING_SESSION`이에요 (`spring.session.jdbc.table-name`). search_path에 맡길지, 스키마를 붙인 이름을 쓸지 정해요.
- **ShedLock(S12):** 잠금 테이블도 같은 스키마에 둬요.
- **search_path 지정:** PostgreSQL JDBC의 `currentSchema` 연결 속성으로 지정할 수 있어요. 다만 Supabase의 Session pooler가 연결할 때 이 값을 넘겨주는지는 확인하지 못했어요 (추측). 안 되면 역할에 기본 search_path를 거는 방법(`ALTER ROLE … SET search_path`)이나, 위처럼 설정마다 스키마를 적는 방법이 있어요.
- **역할과 권한:** 마이그레이션 역할이 스키마를 소유하고, 앱 역할에는 스키마 사용 권한과 테이블 권한만 줘요 (9.4). 운영 DB에 역할을 만드는 건 사람 할 일(H-07)이에요.

**A를 받아들이면 바뀌는 것**

| 문서 | 바뀌는 곳 | 누가 |
|---|---|---|
| `docs/spec/infra.md` 9.4 | "`public`이 아닌 별도 스키마" → 이름 `studybot`을 적음 | 사람 |
| `docs/spec/data-model.md` 머리 안내 | "첫 마이그레이션 전에 스키마 이름(OD-06)이 정해져야" → 정해졌다고 정리 | 사람 |
| `docs/spec/infra.md` 9.10 점검표 | (선택) "앱 테이블이 `studybot` 스키마에 있고 `public`에 없음" 추가 | 사람 |
| 첫 마이그레이션 단계 | 위 '적용 방법'을 판단 기록으로 정하고 설정에 반영 | 에이전트 |

**다른 선택지를 고르면 바뀌는 것**

- B: 위 표에서 이름만 `app`으로 바뀌어요.
- C: 모듈마다 스키마와 권한을 만들고, 모듈 사이 외래 키와 Flyway·Hibernate·Spring Session의 여러 스키마 설정을 정해야 해요. `auth` 모듈의 스키마는 다른 이름을 써야 해요.

## 근거

모두 2026-11-22에 확인했어요.

- [Supabase Docs — Using Custom Schemas](https://supabase.com/docs/guides/api/using-custom-schemas) (2026-09-03 갱신): `public`은 Data API에 기본 노출, 별도 스키마는 'Exposed schemas'에 넣고 권한을 줘야 노출.
- [Supabase Docs — Securing your API](https://supabase.com/docs/guides/database/hardening-data-api) (2026-11-19 갱신): Data API를 끄면 권한·RLS와 상관없이 자동 REST 엔드포인트가 응답하지 않음. 노출하지 않을 테이블은 노출하지 않는 스키마에.
- [Supabase Docs — Managing User Data](https://supabase.com/docs/guides/auth/managing-user-data): Supabase Auth가 `auth.users`(`auth` 스키마)를 씀.
- [Spring Boot 4.1 — Application Properties](https://docs.spring.io/spring-boot/4.1/appendix/application-properties/index.html): `spring.flyway.schemas`, `spring.flyway.default-schema`, `spring.flyway.create-schemas`(기본 `true`), `spring.session.jdbc.table-name`(기본 `SPRING_SESSION`), `spring.jpa.properties.*`.
- [PostgreSQL JDBC — Connection parameters](https://jdbc.postgresql.org/documentation/use/): `currentSchema`는 연결의 search_path에 들어갈 스키마.
- 버전: [spring-boot-dependencies 4.1.3 POM](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.3/spring-boot-dependencies-4.1.3.pom)이 Flyway 12.4.0, PostgreSQL JDBC 42.7.14, Hibernate 7.4.15를 관리.

**확인하지 못한 것 (추측)**

- Supabase가 프로젝트에 만드는 스키마 이름 전체 목록 (`studybot`이 겹치지 않는지는 대시보드에서 확인)
- Supabase Session pooler가 `currentSchema`(연결 시작 시 search_path)를 넘겨주는지
- `app` 같은 흔한 이름을 다른 도구가 쓸 가능성

## 검증 방법

- **첫 마이그레이션 단계:** Testcontainers 테스트에서 테이블이 `studybot` 스키마에 생기고 `public`에는 없는지 확인해요. JPA `ddl-auto=validate`(data-model 마이그레이션 원칙)가 통과하는지도 봐요.
- **운영 준비(9.10, 사람):** Data API가 꺼져 있고, 앱 테이블이 `public`에 없는지 대시보드에서 확인해요.
- **연결:** 운영 DB에 Session pooler로 붙었을 때 search_path가 의도대로인지 첫 배포 전에 확인해요. 안 되면 '적용 방법'의 다른 방법으로 바꿔요.

<!-- 여기부터 사람이 써요. 에이전트는 비워 둬요 -->

## 결정

## 검토 메모
