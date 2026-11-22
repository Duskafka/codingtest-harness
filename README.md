# studybot

코딩테스트 스터디의 인증·알림·정산을 자동으로 운영하는 서비스예요. 멤버가 어느 플랫폼에서든 풀고 GitHub에 커밋하면 push 웹훅으로 자동 인증하고, 마감 전에 못 푼 사람에게만 알리고, 매일 판정해요. 제품 정의는 [`docs/spec/product.md`](docs/spec/product.md)에 있어요.

이 레포는 하네스 방식으로 만들어요. 에이전트(Claude Code)가 단계 하나씩 구현하고 판단을 기록하면, 사람이 그 기록을 검토하고 결정해요 (D24, [`docs/workflow.md`](docs/workflow.md)).

## 기술 스택

Java 25 · Spring Boot 4.1 · Spring MVC · Spring Data JPA · PostgreSQL 17 · Flyway · Gradle (D2, [`docs/spec/architecture.md`](docs/spec/architecture.md) '기술 스택')

## 빌드와 테스트

```bash
./gradlew build                                                    # 전체 빌드와 테스트
./gradlew test --tests 'com.duskafka.studybot.<패키지>.<클래스>'    # 테스트 하나
```

Testcontainers를 쓰는 테스트는 Docker가 필요해요. `docker info`가 실패하면 [`docs/workflow.md`](docs/workflow.md) 8장을 봐요.

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/README.md`](docs/README.md) | 전체 문서 지도, 문서 안 번호(`5.14`, `R4`, `T7`, `D5`, `OD-01` 등) 찾기 |
| [`docs/steps/README.md`](docs/steps/README.md) | 단계 로드맵과 진행 상태, 사람 할 일 |
| [`docs/decisions/README.md`](docs/decisions/README.md) | 확정 결정과 열린 결정 |
| [`docs/workflow.md`](docs/workflow.md) | 작업 방식, 판단 기록 양식, 완료 기준 |
| [`docs/progress.md`](docs/progress.md) | 세션 인계 기록 |
| [`CLAUDE.md`](CLAUDE.md) | 에이전트 진입점 |
