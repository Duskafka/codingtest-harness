# 진행 기록

세션을 끝낼 때 맨 위에 더해요. 양식은 `docs/workflow.md` 7장에 있어요. 최신 기록이 위에 와요.

## 2026-11-21 S01 공통 기반 (세션 1/1)
- 한 일: 모듈 패키지와 ArchUnit 규칙(예제로 자체 검증), `Clock` 주입과 `now()` 금지 규칙, 테스트용 시계(`@IntegrationTest`, `MutableClock`), problem+json 오류 형식, Actuator health만, springdoc(기본 꺼짐), `scripts/cloud-session-setup.sh`, 결정 제안서 001
- 상태: 빌드 통과 (테스트 58개)
- 다음: 결정 제안서 001 검토와 PR 병합 → S02 시간 계산(`StudyCalendar`). S03도 선행이 충족됨
- 막힌 것: 사람 할 일 H-00(master 보호 규칙 꺼져 있음). H-09는 설정 스크립트에 저장했지만, 새 컨테이너에서 실행되는지는 아직 확인 못 함
- 참고: 통합 테스트는 `@IntegrationTest`를 붙이고 시각은 `MutableClock`으로 정한다. 테스트 전용 엔드포인트는 테스트 클래스 안의 클래스로 두고 `@Import`한다
- PR: #1

## 2026-11-21 하네스 문서 추가 (사람)
- 한 일: CLAUDE.md, docs/(spec, steps, decisions, workflow), .claude/(훅, spec-reviewer), PR 템플릿
- 다음: 사람 할 일 H-00, H-09를 마치고 S01 시작
- 막힌 것: 열린 결정 OD-01, OD-02, OD-05, OD-06은 S04 전에 정해야 함

## 2026-11-21 S00 첫 커밋 (에이전트, master 직접)
- 한 일: Gradle 9.9.2 wrapper, Spring Boot 4.1.3 · Java 25 빌드, Testcontainers(`postgres:17`) contextLoads 테스트, CI(`build` 잡), 최소 README
- 상태: 첫 세션 보고 기준. CI 결과는 사람이 확인
- 남긴 것: JDK 25 설치 명령과 dockerd 실행 명령은 첫 세션 보고에 있음 (H-09, S01에서 사용)
