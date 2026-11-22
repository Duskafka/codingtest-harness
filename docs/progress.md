# 진행 기록

세션을 끝낼 때 맨 위에 더해요. 양식은 `docs/workflow.md` 7장에 있어요. 최신 기록이 위에 와요.

## 2026-11-22 OD-01 결정 제안서 (S04 게이트 준비, 단계 진행 아님)
- 한 일: 사람 요청으로 결정 제안서 002(로그인 유지 방식과 만료 정책) 초안. 추천은 ① DB 세션(Spring Session JDBC) ② 30일 롤링 + 절대 만료 ③ SameSite=Lax + `__Host-` 접두사. 근거는 Spring Session 4.1·Security 7.1 문서, OWASP, RFC 10017·9700·8725·8252
- 상태: 문서만 바꿈 (코드 변경 없음)
- 다음: 사람이 002의 '결정'을 채우고 결정 대장 OD-01 행·제안서 목록을 고침 → OD-05·OD-06도 정해지면 S04 시작 (S04는 OD-02 제안서부터)
- 막힌 것: 결정 대기 OD-01. 사람이 정할 값: 절대 만료 기간
- 참고: 이번 세션에서 사람이 답한 조건 — Android·iOS 앱은 P1 이후 계획(스펙 2.4와 달라 스펙 변경 제안), 로그아웃·탈퇴는 즉시 무효화, 추천 기준은 보안·운영 단순성
- PR: (열면 채움)

## 2026-11-22 S03 문제 식별 파서 (세션 1/1)
- 한 일: `verification`에 `ProblemParser.parse(message, paths) → ParsedCommit`(식별한 문제 + 확인 대기 후보), `ProblemRef`, `Platform`(BOJ·PROGRAMMERS·LEETCODE), 파서 버전 1. 커밋 샘플 18개(`src/test/resources/commit-samples/`, 형식은 그 폴더 README)
- 상태: 빌드 통과 (테스트 189개)
- 다음: PR 검토와 병합. 판단 기록 '입출력 모양'·'Platform 값'·'ProblemRef 위치'(꼭 봐 주세요), 스펙 변경 제안(5.4 표), data-model에 파서 버전 컬럼 없음 확인 → 다음 단계 S06(OD-05·OD-06 결정 뒤) 또는 S04
- 막힌 것: 사람 할 일 H-01. 샘플이 모두 '추측'(백준허브 manifest 1.4.29·LeetHub 공개 소스로 만듦). 실제 커밋을 받으면 `"실제"` 샘플로 더한다
- 참고: 백준허브 일괄 업로드(`전체 코드 업로드 -BaekjoonHub`)는 결과 없음 — 세션 중 사람이 정함. 경로 접두사로 확인 대기를 정하는 건 S08 몫이고, `progress`가 `ProblemRef`를 써야 하면 S09에서 `common`으로 옮길지 판단
- PR: #3

## 2026-11-22 S02 시간 계산 (세션 1/1)
- 한 일: `common.time`에 `StudyTimeRule`(R1 범위 검증)과 `StudyCalendar`(`studyDayOf`, `boundaryOf`, `isInGrace`, `reminderAt`, `judgeAt`, `isRestDay`). 서머타임 경계는 뉴욕·산티아고·로드하우 2026년 전환일로 테스트
- 상태: 빌드 통과 (테스트 113개)
- 다음: PR 검토와 병합. 판단 기록 '시간 규칙을 넘기는 방식'(꼭 봐 주세요)과 열린 질문(OD-08 전환일의 유예 규칙) 확인 → 다음 단계 S03(문제 식별 파서, H-01 샘플이 있으면 사용)
- 막힌 것: 없음. 규칙 전환일 경계는 OD-08 결정 대기 (S05에서)
- 참고: 다른 모듈은 그날의 규칙에서 `StudyTimeRule`을 만들어 `StudyCalendar.of(...)`로 쓴다. 시간 계산을 직접 하지 않는다
- PR: #2

## 2026-11-21 S01 공통 기반 리뷰 반영 (세션 2)
- 한 일: PR #1 CodeRabbit 리뷰 4건 반영. 반환값 검증 실패는 `errors` 없이 500, 객체 전체 오류의 `field`는 `null`, `field`는 요청에 쓴 이름(`@RequestParam("page_size")`), 세션 준비 스크립트의 `docker info`에 시간 제한. H-00 완료 확인(master `protected: true`). 결정 001 확정 기록(사람이 ① A·② A·③ A 선택, 요청에 따라 에이전트가 받아 적음)
- 상태: 빌드 통과 (테스트 64개)
- 다음: PR #1 병합 → 사람이 결정 대장의 '결정 제안서 목록'과 스펙 변경 제안(architecture 4.2) 반영 → 새 세션에서 S02 시간 계산(`StudyCalendar`)
- 막힌 것: 없음. H-09는 새 컨테이너에서 확인 필요 (이번 재시작은 같은 컨테이너를 이어 써서 설정 스크립트가 돌지 않음)
- PR: #1

## 2026-11-21 S01 공통 기반 (세션 1/1)
- 한 일: 모듈 패키지와 ArchUnit 규칙(예제로 자체 검증), `Clock` 주입과 `now()` 금지 규칙, 테스트용 시계(`@IntegrationTest`, `MutableClock`), problem+json 오류 형식, Actuator health만, springdoc(기본 꺼짐), `scripts/cloud-session-setup.sh`, 결정 제안서 001
- 상태: 빌드 통과 (테스트 61개)
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
