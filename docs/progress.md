# 진행 기록

세션을 끝낼 때 맨 위에 더해요. 양식은 `docs/workflow.md` 7장에 있어요. 최신 기록이 위에 와요.

## 2026-11-23 S05 스터디·규칙·기수·초대·멤버십 (세션 1)
- 한 일: 만들 것 1·2·3·5·6·7·8·9. V3 마이그레이션(studies, study_rules, seasons, memberships, invitations), 만들기(규칙 v1·1기·방장 한 트랜잭션), 목록(PagedModel)·상세, 이름 변경·위임, 초대 만들기·철회·미리보기(공개)·수락, 탈퇴, `StudyAccess`(비멤버 404, 비방장 403). 정원 동시성은 스터디 행 잠금 + 조건부 UPDATE(철회·만료도 다시 봄), 결정 제안서 006. `StudyCalendar`에 `nextStudyDayOf`·`seasonEndDay` 추가
- 상태: 빌드 통과 (테스트 320개). 구현 전 새 테스트 73개 중 72개 실패 확인. 잠금·UPDATE 조건을 하나씩 빼면 해당 테스트가 실패하는 것도 확인. spec-reviewer 1차 12건·2차 11건 처리(PR 본문)
- 사람이 정한 것 (2026-11-23, 세션 중 선택지): 시작일 "다음 날~30일 뒤 (추천)", 초대 철회 "DELETE 엔드포인트 추가 (추천)". 선택지 설명 원문과 에이전트가 해석한 부분은 PR 본문에 나눠 적음
- 다음: PR 검토 → 사람이 '지금 병합할지, OD-08까지 열어 둘지'(PR 열린 질문 1)와 결정 제안서 006을 정함 → 병합 뒤 `docs/` 문서 전용 PR로 두 결정과 받아들인 스펙 변경 제안을 반영 → OD-08이 정해지면 규칙 변경 `PUT /rules`·T11
- 막힌 것: 결정 대기 OD-08 (규칙 변경 API, T11). OD-07은 1기 생성만 했고 다음 기수는 S12. 단계 상태는 '결정 대기'
- 참고: S05 테스트는 `study/StudyTestSupport`(사용자는 DB에 넣고 `authentication()`으로 로그인, CSRF는 쿠키로)를 상속. 기수 SCHEDULED→ACTIVE 전환은 아직 어느 단계에도 없음(PR 열린 질문). Hibernate 7.4의 PostgreSQL 쓰기 잠금은 `FOR NO KEY UPDATE`
- PR: #11

## 2026-11-22 S04 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #9의 '스펙 변경 제안' 두 건을 사람이 받아들여 문서 전용 PR로 반영했다
    - architecture 4.1 Nginx 경로 목록에 `/logout`·`/github/setup`·`/actuator/health`를 더했다. 같은 목록이 있는 S17 '만들 것' 3도 함께 고쳤다
    - data-model 4.3 `users` 제약 칸을 "github_id UNIQUE, 탈퇴 전 필수 (CHECK). 탈퇴 시 익명화·deleted_at 채움 (5.16)"으로 바꿨다. S15 '만들 것' 1의 익명화에도 `deleted_at`을 함께 채운다고 적었다 (사람이 고른 답의 설명 문장)
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎). 확정된 결정 제안서 002의 같은 목록(24행)은 기록이라 그대로 둠. `/admin/**`이 Nginx 목록에 없는 것은 PR '열린 질문'에 올림
- 다음: 이 PR 병합 → S05(스터디·멤버십, OD-07·OD-08 결정 필요) 또는 S06(웹훅 수신)
- 막힌 것: 없음
- 참고: 훅은 `docs/S[0-9]*` 브랜치를 단계 브랜치로 봐서 스펙 수정을 막는다. 문서 전용 PR 브랜치 이름은 `docs/S`로 시작하지 않게 한다
- PR: #10

## 2026-11-22 S04 로그인과 세션 구현 (세션 1, 이어서)
- 한 일: '만들 것' 2~8. GitHub App 사용자 인증 로그인(`/user`에서 id·login·avatar만, users native upsert), DB 세션(`studybot.SPRING_SESSION`), 세션 쿠키 `__Host-Http-SESSION` 빈 설정과 30일 롤링 필터, `csrf.spa()`, `/webhooks/**` 별도 체인, 401·403 problem+json, `GET /api/me`, `POST /logout` 204, 토큰 미저장 저장소. 첫 엔티티·마이그레이션이라 Lombok(`lombok.config`)·`studybot` 스키마 설정도 함
- 상태: 빌드 통과 (테스트 238개). 구현 전 새 테스트 27개 중 25개 실패 확인. spec-reviewer 1차 지적 8건 반영(스타터 이름, github_id NULL 허용 + CHECK, 만료 경계·비로그인 로그아웃·Actuator POST 테스트, 엔티티 ArchUnit 규칙). 2차 지적 6건 반영(세션에 접속 IP를 남기지 않음, setter 규칙을 `set[A-Z].*`로, Actuator POST 검사를 404로, 만료 테스트를 JVM 시각 기준으로, data-model CHECK를 스펙 변경 제안 2번으로). Chromium 157로 `http://localhost`·`127.0.0.1`에서 `__Host-Http-` 쿠키 저장 확인
- 사람이 정한 것 (PR을 연 뒤, 2026-11-22): 열린 질문 1은 "주석만 더함"(S01 Actuator 테스트의 POST 줄에 주석만, 기대값 그대로, `4abdee6`). 스펙 변경 제안 1(architecture 4.1 Nginx 목록에 `/logout`·`/github/setup`·`/actuator/health`)은 "세 경로 추가", 제안 2(data-model 4.3 `users`에 '탈퇴하지 않은 사용자는 github_id 필수' CHECK)는 "받아들임"
- 다음: PR 검토와 병합 → 병합 뒤 `docs/` 브랜치의 문서 전용 PR로 위 스펙 변경 제안 1·2를 반영(근거는 위 선택지 원문과 날짜) → S05(스터디·멤버십, OD-07·OD-08 결정 필요) 또는 S06(웹훅 수신). S06은 `/webhooks/**` 체인(CSRF 끔, STATELESS)을 그대로 쓰고, S07은 `/github/setup`에 로그인 규칙을 더해야 함
- 막힌 것: 없음. 실제 GitHub 로그인 확인은 사람 할 일 H-02(개발용 App, 콜백 URL `/login/oauth2/code/github`, 환경 변수 `GITHUB_APP_CLIENT_ID`·`GITHUB_APP_CLIENT_SECRET`)
- 참고: 사람이 정한 것 — 기존 `ErrorResponseIntegrationTest` 4개는 요청에만 `user()`·`csrf()`를 붙임(기대값 그대로). Boot 4.1은 MockMvc 환경에서 `server.servlet.session.cookie.*`를 쓰지 않아 쿠키를 빈으로 정함. Lombok 1.18.48은 `@SuperBuilder`를 `flagUsage`로 막지 못함. 공유 테스트 컨텍스트에서 `csrf()` 후처리기를 쓰면 뒤 테스트의 XSRF-TOKEN 쿠키가 사라짐(쿠키로 토큰을 받을 것). XSRF-TOKEN의 Secure는 요청이 HTTPS일 때만 붙어서 S17에서 프록시 헤더 설정이 필요함. 작업 프로세스가 다시 시작된 뒤 dockerd가 containerd 시작 대기 시간 초과로 죽어 있으면 `CLAUDE_CODE_REMOTE=true bash scripts/cloud-session-setup.sh`를 다시 실행하면 됨
- PR: #9

## 2026-11-22 S04 로그인과 세션: OD-02 결정 제안서와 결정 반영 (세션 1)
- 한 일: S04 '만들 것' 1. 결정 제안서 005(GitHub 로그인 방식, A 별도 OAuth App / B GitHub App 사용자 인증 / C 설치 중 인증 비교, 추천 B). 사람이 B를 고른 뒤 같은 PR에서 005 '결정'·'검토 메모'를 받아 적고, 결정 대장(OD-02, 제안서 목록, ADR-002)·스펙(external-systems 4.6, infra 9.6, architecture '로그인' 행, api.md 머리·4.5, 5.02)·단계 문서(S04, steps/README의 H-02·H-03·S04 행)에 반영. S04 상태는 '대기'
- 상태: 빌드 통과 (테스트 189개, 제안서 커밋 때). 반영은 문서만 바꿔 다시 돌리지 않음 (CI가 돎). 첫 빌드는 Maven Central 429로 실패했고, 다시 돌려 통과
- 결정: OD-02 = B (추천 그대로). H-03은 H-02에 합침. 랜딩 '받는 정보' 문구(product.md 2.5)는 지금 그대로
- 다음: PR #8 병합 → S04 '만들 것' 2부터 (새 브랜치 `feat/S04-login-session`)
- 막힌 것: 없음 (H-02는 실제 로그인 확인 때만 필요)
- 참고: 사람이 이번 세션에서 답한 것 — 추천 기준은 보안·운영 단순성(OD-01과 같음), 브랜치는 `docs/OD-02-github-login`. S04에서 정할 것(005 '양쪽 공통'): 토큰을 남기지 않는 구성(Boot 기본 `InMemoryOAuth2AuthorizedClientService`가 토큰을 메모리에 둠), PKCE가 붙는지 테스트(Spring Security 7.x는 `requireProofKey` 기본값이 `true`), 신원 확인 직후 토큰 취소 여부. 세션 시작 때 로컬 `origin/master`가 오래돼 PR #7 병합을 늦게 봤음. 시작할 때 `git fetch origin master`부터 하기
- PR: #8

## 2026-11-22 OD-01·OD-05·OD-06 결정 반영 (docs PR, 단계 진행 아님)
- 한 일: 새 규칙의 첫 문서 반영. 결정 대장(OD-05·06 확정, 제안서 목록 003·004, D22 표기), 스펙(architecture '세션'·'Lombok' 행, infra 9.4 `studybot`, data-model 머리, api.md 머리·4.5, security.md), 단계 문서(S04 머리 안내·읽을 문서·만들 것 3·8·수용 기준, S06 읽을 문서·만들 것 8, S17 6·7)
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 다음: 이 PR 병합 → S06(웹훅 수신, 첫 엔티티·마이그레이션이면 Lombok·`studybot` 설정 포함) 또는 S04(OD-02 제안서부터)
- 막힌 것: 없음. S04 구현은 OD-02 결정 대기
- 참고: PR #6의 선택 후속 패치(보호 경로 문구, 소문자 단계 브랜치)는 아직 적용 안 됨
- PR: #7

## 2026-11-22 하네스 변경: 사람이 정한 결정의 문서 반영을 에이전트에게 (단계 진행 아님)
- 한 일: 사람 요청으로 규칙을 바꿈. 사람이 정한 결정은 에이전트가 `docs/` 브랜치의 문서 전용 PR(`docs/`·루트 `README.md`만)로 스펙·결정 대장·단계 문서에 반영하고, PR '반영하는 결정'에 근거를 적음. workflow.md 2·3·4·5·9장, 문서 머리 안내(스펙 14개·결정 대장·docs/README·steps), 루트 README를 짧은 소개로. 훅·CLAUDE.md·spec-reviewer·harness-log·PR 템플릿은 사람이 패치로 적용(`d529511`)
- 상태: 빌드 확인 안 함 (문서·하네스만 바꿈, CI가 돎). 실제 세션에서 훅 확인: docs 브랜치에서 스펙·결정 대장·README 수정 통과, `docs/` 밖·`.claude/` 수정은 막힘
- 다음: PR #6 병합 → 새 docs PR로 OD-05·06(PR #5에서 확정)과 OD-01 남은 것을 결정 대장(OD-05·06 행, 제안서 목록 003·004, D22)·스펙·단계 문서에 반영 → S06 또는 S04 (S04는 OD-02 제안서부터)
- 막힌 것: 없음 (PR #6 검토·병합 대기)
- 참고: 단계·구현 브랜치(`feat/…`, `docs/S<번호>-…`)에서는 스펙·결정 대장을 못 고침. 스펙이 틀렸으면 '스펙 변경 제안'에. PR #5가 먼저 병합돼 master를 이 브랜치에 병합하고 이 파일 충돌을 풀었음
- PR: #6

## 2026-11-22 OD-05·OD-06 결정 제안서 (S04·S05·S06 게이트 준비, 단계 진행 아님)
- 한 일: 사람 요청으로 결정 제안서 003(Lombok, 추천 B: 허용 목록 방식 — `@Getter`, 보호된 기본 생성자, `@RequiredArgsConstructor`, `@Slf4j`만 허용하고 나머지는 `lombok.config`로 막음)과 004(DB 스키마 이름, 추천 `studybot` 하나) 초안
- 상태: 빌드 통과 (테스트 189개, 문서만 바꿈)
- 결정: 사람이 정한 내용을 003·004에 받아 적음 — OD-05는 B(허용 목록) + `@Builder` 허용, OD-06은 에이전트 추천대로 `studybot`
- 다음: 하네스 PR(사람이 정한 결정을 에이전트가 docs 브랜치에서 문서에 반영)이 병합되면, 이 PR에서 결정 대장(OD-05·06 행, 제안서 목록 003·004, D22)과 스펙·단계 문서(architecture 'Lombok'·'세션' 행, infra 9.4, data-model 머리, OD-01 남은 것, S04·S05·S06 '읽을 문서'·'만들 것', S17 6·7)를 반영 → S06 또는 S04 시작 (S04는 OD-02 제안서부터)
- 막힌 것: 하네스 변경 대기 (훅·CLAUDE.md는 사람이 적용)
- 참고: 이번 세션에서 사람이 답한 것 — OD-05 기준은 읽기 쉬움, OD-06 이름은 `studybot`. Lombok 애너테이션은 SOURCE 보존이라 ArchUnit으로 검사할 수 없음(jar에서 확인). PR #4와 이 기록 위치가 겹쳐 나중에 병합되는 쪽에서 충돌을 풀어야 함
- PR: #5

## 2026-11-22 OD-01 결정 제안서 (S04 게이트 준비, 단계 진행 아님)
- 한 일: 사람 요청으로 결정 제안서 002(로그인 유지 방식과 만료 정책) 초안. 추천은 ① DB 세션(Spring Session JDBC) ② 30일 롤링 + 절대 만료 ③ SameSite=Lax + `__Host-Http-` 접두사. 근거는 Spring Session 4.1·Security 7.1 문서, OWASP, RFC 10017(7.1)·9700·8725·8252
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 결정: 사람이 정한 내용을 002에 받아 적음 — ① DB 세션 ② 30일 롤링만(추천과 다름) ③ SameSite=Lax + `__Host-Http-`. 검토 메모의 '이유'는 사람이 채울 칸으로 남김
- 다음: PR #4 병합 → 사람이 결정 대장(OD-01 행 '확정', 제안서 목록 002, D22 문장)과 스펙·S04 문서(쿠키 접두사, '세션' 행 확정, OD-01 안내 정리)를 고침 → OD-05·OD-06도 정해지면 S04 시작 (S04는 OD-02 제안서부터)
- 막힌 것: 결정 대장·스펙 반영 대기 (사람)
- 참고: 이번 세션에서 사람이 답한 조건 — Android·iOS 앱은 P1 이후 계획(스펙 2.4와 달라 스펙 변경 제안), 로그아웃·탈퇴는 즉시 무효화, 추천 기준은 보안·운영 단순성
- PR: #4

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
