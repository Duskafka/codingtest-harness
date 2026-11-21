# studybot

코딩테스트 스터디의 인증·알림·정산을 자동으로 운영하는 서비스다. 멤버가 어느 플랫폼에서든 풀고 GitHub에 커밋하면 push 웹훅으로 자동 인증하고, 마감 전에 못 푼 사람에게만 알리고, 매일 판정한다. 제품 정의는 `docs/spec/product.md`.

이 레포는 하네스 방식으로 만든다. 너는 단계 하나씩 구현하고 판단을 기록한다. 사람은 그 기록을 검토하고 열린 결정을 정한다.

## 세션 시작

1. `docs/progress.md`의 최근 기록과 `git log --oneline -15`를 읽는다.
2. 열린 PR을 확인한다 (세션의 GitHub 도구나 `gh api repos/Duskafka/codingtest-harness/pulls`). 이어서 할 PR이나 리뷰를 반영할 PR이 있으면 그 브랜치로 바꾸고 그 브랜치의 `docs/progress.md`를 다시 읽는다. 리뷰 반영은 `docs/workflow.md` 2장 끝.
3. `docs/steps/README.md`에서 맡은 단계를 찾는다. 사람이 지정하지 않았으면 선행 단계가 모두 '완료'이고 열린 PR이 없는 첫 '대기' 단계를 고른다.
4. 단계 문서 `docs/steps/S??-*.md`를 읽고, 거기 적힌 '읽을 문서'만 읽는다. 스펙 전체를 미리 읽지 않는다.
5. 단계의 결정 게이트를 `docs/decisions/README.md`에서 확인한다. 열린 결정에 걸린 부분은 구현하지 않는다.
6. `./gradlew build`가 통과하는지 확인한다. 실패하면 그것부터 고친다.

흐름, 판단 기록 양식, 완료 기준은 `docs/workflow.md`에 있다.

## 규칙

- 한 세션에 단계 하나, 단계 하나에 PR 하나. master에 직접 push하지 않는다.
- 수용 기준을 먼저 테스트로 옮기고, 실패를 본 뒤 구현한다. 테스트 이름에 T번호를 붙인다.
- 기존 테스트의 기대값을 바꾸거나, 지우거나, 끄지 않는다. 필요하면 PR에서 요청한다.
- `docs/spec/`, `docs/decisions/README.md`, `docs/harness-log.md`, `CLAUDE.md`, `.claude/`, master에 들어간 Flyway 마이그레이션은 고치지 않는다. 훅이 막고, Bash로 우회하지 않는다. 스펙이 틀렸다고 보면 PR의 '스펙 변경 제안'에 쓴다.
- 스펙에 없거나 스펙끼리 부딪히는 동작을 추측으로 채우지 않는다. 작은 판단은 PR의 판단 기록에, 되돌리기 어려운 판단은 결정 제안서(`docs/decisions/`)에 남긴다.
- 모듈 의존 방향은 `docs/spec/architecture.md` 4.2를 따른다. ArchUnit이 검사한다.
- 시간은 주입된 `Clock`으로만 다룬다. `now()`를 직접 부르지 않는다.
- 비밀값, 실제 접속 정보, 개인정보를 코드·설정·로그·테스트 데이터에 넣지 않는다. 실제 키가 필요하면 사람 할 일(H-NN)로 남긴다.
- 기술 기준은 Java 25, Spring Boot 4.1, Hibernate 7, Jackson 3, Testcontainers 2다. 3.x 시절 예제를 그대로 쓰지 말고 공식 문서로 확인해 PR에 링크한다.
- PR을 열기 전에 `spec-reviewer` 서브에이전트에게 diff 검토를 맡긴다.
- 세션을 끝내기 전에 `docs/progress.md`에 인계 기록을 쓴다.

## 명령

- 전체 빌드와 테스트: `./gradlew build`
- 테스트 하나: `./gradlew test --tests 'com.duskafka.studybot.<패키지>.<클래스>'`
- Testcontainers는 Docker가 필요하다. `docker info`가 실패하면 `docs/workflow.md` 8장을 본다.

## 문서 지도

- `docs/README.md`: 전체 지도. 문서 안의 `(5.14)`, `9.7`, `R4`, `T7`, `D5`, `OD-01`, `[S33]` 같은 번호를 어디서 찾는지 적혀 있다.
- `docs/spec/`: 무엇을 만드는지 (계약)
- `docs/steps/`: 단계별 작업 문서와 로드맵
- `docs/decisions/`: 결정 대장과 결정 제안서
- `docs/workflow.md`: 작업 방식 / `docs/progress.md`: 세션 인계 기록
