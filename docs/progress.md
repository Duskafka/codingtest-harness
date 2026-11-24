# 진행 기록

세션을 끝낼 때 맨 위에 더해요. 양식은 `docs/workflow.md` 7장에 있어요. 최신 기록이 위에 와요.

## 2026-11-24 S08 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #18(병합)의 스펙 변경 제안 1~3을 사람이 골라 문서에 반영. spec-reviewer '수정 필요' 9건 중 5건은 문서를 고침(사람이 고르지 않은 정규화 문장·`return PROCESSED` 빼기, 부작용 문구 바로잡기), 나머지는 PR 근거·열린 질문으로. data-model `solve_records`(표에 `parser_version`·경로 UNIQUE, 새 메모 '풀이 기록 테이블': 경로 UNIQUE의 목적과 부작용, CHECK), `linked_repositories`의 빈 `path_prefix`, 5.4 흐름도와 '확인 대기' 단서(레포 전체, 커밋마다 하나, P1에는 설정 없음), pseudocode `processPush` 순서·확인 대기 분기, 5.3 'push 처리 순서' 1·2(항목 번호는 그대로)
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것 (2026-11-24, 세션 선택지): 제안 1 "셋 다 반영", 제안 2 "data-model과 5.4 둘 다", 제안 3 "pseudocode와 5.3 둘 다" (모두 Recommended)
- 다음: 이 PR 병합 → S09(선행 S05가 '결정 대기'라 OD-07·OD-08을 정하거나 S05를 어떻게 볼지 먼저 정해야 함)
- 막힌 것: 없음
- 참고: 5.3 'push 처리 순서'는 코드 주석이 번호(2·3·6)를 가리켜서 항목을 합치기만 하고 번호를 밀지 않았다
- PR: #19

## 2026-11-24 S08 push 처리와 풀이 기록 (세션 1/1)
- 한 일: 만들 것 1~7. V7 `solve_records`(표 + `parser_version`, CHECK, 부분 UNIQUE 둘), `github.push.PushEventHandler`(삭제·연결 없음·기본 브랜치 아님 → IGNORED), `verification.GithubSolveRecorder`(문제마다 CONFIRMED, 식별 못 한 커밋은 접두사 안 첫 후보로 확인 대기 하나, 인정 시각 = 수신함 `received_at`), 배정 연결 지점 `progress.StudyDayAssignment`(새 CONFIRMED마다 같은 트랜잭션, 구현은 아무것도 안 함), 네 이벤트 처리기 필수(`GithubEventHandlers#requireAllEvents`)
- 상태: 빌드 통과 (테스트 500개). 구현 전 새 테스트 36개 중 34개 실패 확인. 조건 빼 보기 5개 모두 해당 테스트 실패. spec-reviewer '통과'(코드 지적 없음, PR 본문 항목은 반영)
- 사람이 정한 것 (2026-11-24, 세션 선택지): 빈 `path_prefix` "레포 전체로 봄", 확인 대기 "커밋마다 하나", 브랜치 "feat/S08-push-to-solve" (모두 Recommended)
- PR 열린 질문 결정 (2026-11-24, PR을 연 뒤 세션 선택지): 1 기본 브랜치는 "레포마다 기본 브랜치 (Recommended)"(처음 지시는 "기본 브랜치는 master로 지정해"였고, master만 인정하면 기본 브랜치가 main인 레포는 모두 무시된다고 다시 물어 고름) → 연결 행의 `default_branch`와 비교하는 지금 구현 그대로. 2 경로 접두사 설정은 "지금은 만들지 않음 (Recommended)" → 레포 전체를 보는 지금 동작 유지, 확인 대기가 많이 쌓이면 다시 정함. 코드 변경 없음
- 다음: PR 검토(판단 기록 '꼭 봐 주세요': 배정 연결 지점, 테이블 제약·`raw_path` UNIQUE) → 병합 뒤 받아들인 스펙 변경 제안을 docs PR로 → S09(배정, 선행 S02·S05·S08. S05는 '결정 대기'라 선행 조건 확인 필요)
- 막힌 것: 없음. 실제 push 확인은 H-02
- 참고: S09는 `progress.PendingStudyDayAssignment`를 실제 배정으로 바꾸면 된다(`SolveToAssign`에 기록 ID·사용자·플랫폼 문자열·번호·인정 시각). S06 웹훅 테스트의 `RecordingPushHandler`는 이제 실제 push 처리기를 감싸고 기본은 PROCESSED라, 실제 처리기를 쓰려면 `passThrough()`. 배정 연결 지점은 `RecordingStudyDayAssignment`로 호출을 본다
- PR: #18

## 2026-11-24 S07 후속: 설치 삭제 시각 deleted_at (V6, 같은 세션)
- 한 일: V6(`github_installations.deleted_at` 추가 → V5 시절 DELETED 행을 `updated_at`으로 백필 → CHECK `(status = 'DELETED') = (deleted_at IS NOT NULL)`). 아는 설치의 deleted(`markDeleted`)와 처음 보는 설치의 deleted(`recordStateIfAbsent`)가 삭제 시각을 채운다. 테스트: 늦은 removed·deleted·created가 삭제 시각을 바꾸지 않음(시계를 앞으로 돌려 확인), CHECK, V5→V6 백필(따로 띄운 컨테이너)
- 상태: 빌드 통과 (테스트 463개). 새 테스트는 V6 없이 실패 확인(컬럼 없음, V6 없음). 백필 UPDATE를 빼면 백필 테스트가 실패하는 것 확인
- 사람이 정한 것 (2026-11-24, 세션 선택지): '삭제된 지 30일'의 기준 "deleted_at 컬럼", #14가 먼저 병합돼서 "새 후속 PR로 V6", 원격 feat/S07-github-app에 남은 43359e4는 "그대로 둠" (모두 Recommended). docs PR #15의 열린 질문 결정: "계정 값만 비움", "API로 설치 삭제" (S15에서 구현)
- 다음: 이 PR 병합 → docs PR #15 병합(deleted_at 문서가 이 PR에 기댐) → H-02(실제 App으로 확인) → S08
- 막힌 것: 없음
- 참고 (실수와 교훈): #14가 01:47Z에 병합된 것을 모르고, 그 뒤에 feat 브랜치에서 V5를 고친 커밋(43359e4)을 push했다. 로컬 origin/master가 병합 전에 멈춰 있어서 보호 훅도 막지 못했다. **작업을 시작하거나 마이그레이션을 고치기 전에 `git fetch origin master`로 병합 여부를 본다.** 43359e4는 master에 없고, 내용은 이 PR로 옮겼다(V5는 그대로)
- PR: #16 (docs PR #15보다 먼저 병합). 재발 방지로 세션 시작 때 origin/master를 받아 오는 chore PR #17(사람이 정함 "스크립트 + 훅 패치 제안"). 세션 도중 병합까지 잡는 보호 훅 패치는 #17 본문에 제안만 했고 적용은 사람 몫

## 2026-11-23 S07 GitHub App 연결 (세션 1/1)
- 한 일: 만들 것 1~6. V5(`github_installations`, `linked_repositories`), 설치 웹훅 처리기(created·deleted·suspend·unsuspend, removed·added), 가입 때 연결(auth `UserLoggedIn` 커밋 뒤 + 연결 화면에서 다시 claim), GitHub API 클라이언트(App JWT를 JDK로 서명, PKCS#1·#8 키, 설치 토큰 메모리 캐시·만료 5분 전 갱신), `GET /api/github/install-url`(서명된 1회용 state), `GET /github/setup`(302), `GET /api/me/github/repositories`(레포 1개면 자동 연결), `PUT /api/me/linked-repository`
- 상태: 빌드 통과 (테스트 432개). 구현 전 새 테스트 52개 중 51개 실패 확인. 조건 빼 보기 8개 모두 해당 테스트 실패. spec-reviewer '수정 필요'(코드 지적 없음, PR 본문 3건) → 반영, 고려 10건 중 6건은 코드·테스트로(설치자 ID 대신 `installed_by_owner`, 저장 때 설치 잠금 재확인, 테스트 3개)
- 사람이 정한 것 (2026-11-23, 세션 선택지): 브랜치 "feat/S07-github-app (Recommended)", S06 테스트 "installation 단언만 PROCESSED로 (Recommended)", 자동 연결 "레포 목록 조회 때 (Recommended)", `/github/setup` "프론트 홈으로 302 (Recommended)"
- 다음: PR 검토(판단 기록 '꼭 봐 주세요': 응답 모양, 테이블 컬럼·상태 값, 엔티티 이름, 가입 연결 방식, state) → 병합 뒤 받아들인 스펙 변경 제안을 docs PR로 → S08(push 처리, 선행 S03·S06·S07 완료)
- 막힌 것: 없음. 실제 연동 확인은 H-02. 새 환경 변수 `GITHUB_APP_SLUG`(필수), `GITHUB_APP_PRIVATE_KEY_PATH`(필수, 키 파일 경로. 키 내용을 환경 변수로 받지 않음, 아래 '스펙 변경 제안 결정'). JWT `iss`는 기존 `GITHUB_APP_CLIENT_ID`
- 참고: S08은 연결을 `repo_id` + ACTIVE로 찾는다(부분 UNIQUE). `default_branch`는 목록 조회 때 GitHub 값으로 갱신된다. 모든 통합 테스트 컨텍스트에 테스트 키·WireMock(`github.GithubApiStub`)이 들어간다(`GithubAppTestConfiguration`). 컨텍스트를 따로 띄우는 테스트는 키를 직접 넣는다(`SessionRestartTest`는 실행 인자로, `properties()`는 yaml 빈 값에 밀림). 커밋 전 파일을 `git checkout --`로 되돌리는 명령은 막히니 조건 빼 보기는 커밋한 뒤 정확히 되바꿔서
- 리뷰 반영 (같은 세션): CodeRabbit 11건 중 9건 코드로(HEAD 500, 웹훅 행 잠금, 순서 바뀐 deleted·suspend 기록, `repositories_revision`으로 제거·연결 경합, 완전한 목록에 없는 연결 끊기, refresh 재읽기, 목록 1000개 범위, `@DynamicUpdate`, 로그인 연결 `@Async`), 2건은 답글만(토큰 기준 해석, state 원자성). spec-reviewer 2차가 찾은 open-in-view 문제(잠근 뒤에도 요청 안의 옛 엔티티로 비교) → 네이티브 `select … for update`로. 빌드 통과(테스트 450개). 사람이 정한 것 (2026-11-23, 세션 선택지): "체크 유지, 해석을 답글로", "세션 유지, 답글로 설명", "목록이 한 페이지로 다 보일 때만 끊음", "비동기로 실행" (모두 Recommended). S13 참고: 연결은 이제 웹훅뿐 아니라 목록 조회(GET)에서도 끊긴다. open-in-view가 켜져 있어 같은 요청에서 앞서 읽은 엔티티는 잠가도 다시 채워지지 않으니, 잠근 뒤 확인은 네이티브 쿼리로
- 2차 리뷰 반영 (같은 세션): CodeRabbit review 6031060345(Caution = 줄에 달 수 없는 지적 1건)와 후속 답글. Caution의 옛 엔티티 비교와 `Thread.sleep` 지적은 이미 고친 것(535d951, 6e916ea)이라 PR 댓글로 알림. 코드로 고친 것 3건: 처음 보는 설치의 deleted·suspend·unsuspend는 설치 ID·상태만 남기고 계정 값·설치자는 created가 `ON CONFLICT DO UPDATE … WHERE installed_by_owner IS NULL AND status <> 'DELETED'`로 채움(V5 네 컬럼 NULL 허용 + CHECK, 1f02e86·bc7f71b, 삭제된 설치에는 채우지 않는 것은 spec-reviewer 3차 지적), 목록에서 빠진 연결은 목록 읽기 전에 본 행(id)·그 설치만 끊음(2605aa5), `total_count` 없는 목록 응답은 503(898c2d4). 빌드 통과(테스트 457개), 조건 빼 보기 5개 모두 걸림. spec-reviewer 3차 '수정 필요'는 PR 본문 갱신(코드 지적 없음). S08 참고: `github_installations`의 계정 값은 NULL일 수 있다(사용자에게 이은 행에는 늘 있음, CHECK)
- 스펙 변경 제안 결정 (같은 세션, 2026-11-24): 사람이 정한 것 (세션 선택지): 제안 1 "Github* 유지", 제안 2 "같은 테이블, NULL 허용", 제안 3 "파일만", 제안 4 "설치 중 + 삭제 뒤 30일" (모두 Recommended), 제안 5는 2026-11-23 결정 그대로. 제안 3 때문에 #14에서 키를 파일 경로로만 받게 고침(3ea7b3e). spec-reviewer 4차가 찾은 '경로 자리에 키 내용을 넣으면 기동 실패 로그에 키가 남음'도 고침(9489220). 빌드 통과(테스트 460개). 문서 반영은 docs PR #15(#14 다음에 병합, progress는 #14에만 적음). #15의 열린 질문 3개(30일 정리 때 연결 행 FK, '삭제된 지 30일'의 기준 시각, 탈퇴 때 설치 행 삭제와 S15 만들 것 3의 관계)는 S15에서 정할 거리
- 다음: #14 병합 → #15 병합 → H-02(실제 App으로 확인) → S08
- PR: #14, docs PR #15

## 2026-11-23 S06 후속 결정 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #12(병합)에서 사람이 정한 것을 문서에 반영. S08 '만들 것' 7·수용 기준(네 이벤트 모두 처리기 필수), pseudocode 'push 웹훅 처리'(ping 200, `last_error`는 예외 종류), data-model `webhook_inbox`(payload 14일 뒤 비움, NULL 허용, 행은 남음), 5.3 '주의할 것'(5MB, 413 + 전달 ID 경고 로그)
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것 (2026-11-23, 세션 중 선택지): 스펙 변경 제안 1·2 "둘 다 받아들임 (Recommended)". 열린 질문 셋은 S06 기록에 있음
- 다음: 이 PR 병합 → 새 세션에서 S07(GitHub App 연결, 선행 S04·S06 완료). S07 처리기는 `github.webhook.GithubEventHandler` 빈으로
- 막힌 것: 없음
- 참고: PR #12 '그 밖에 발견한 것'(OSIV 기본값 켜짐, 수신함 엔티티의 모듈 위치와 P3 디스코드, 헤더 제어 문자 → 500)은 사람이 아직 정하지 않아 문서에 넣지 않음
- PR: #13

## 2026-11-23 S06 웹훅 수신 기반 (세션 1/1)
- 한 일: 만들 것 1~8. V4 `webhook_inbox`, `POST /webhooks/github`(본문 5MB까지 바이트로 읽기 → 서명 → 이벤트 거르기 → 수신함 INSERT ON CONFLICT 별도 트랜잭션 → 새 트랜잭션에서 처리기 + 상태 → 실패면 FAILED), 시크릿 교체(현재·옛), 처리기 인터페이스 `github.webhook.GithubEventHandler`, `PingEventHandler`. 테스트 payload는 octokit/webhooks 예시를 가짜 값으로 바꿈(`github-payloads/README.md`)
- 상태: 빌드 통과 (테스트 376개). 구현 전 새 테스트 42개 중 41개 실패 확인. 트랜잭션을 합치거나 ON CONFLICT를 빼면 해당 테스트가 실패하는 것도 확인. spec-reviewer '통과', 고려할 것 6건 반영(PR 본문). CodeRabbit '지적 없음'
- 사람이 정한 것 (2026-11-23, 세션 중 선택지): 브랜치 "feat/S06-webhook-receiver (Recommended)", ping "수신함에 넣고 200 (Recommended)". PR 열린 질문 셋: 본문 한도 "5MB 유지 + 경고 로그 (Recommended)", 처리기 없는 이벤트 "S08에서 처리기 필수로 (Recommended)", `last_error` "메시지 대신 예외 종류만 (Recommended)" → 1·3은 이 PR에 구현(`cfdad73`), 2는 기록만
- 다음: PR 검토와 병합 → docs PR로 S08 '만들 것'에 "네 이벤트 모두 처리기가 있어야 앱이 뜬다" 추가와 스펙 변경 제안 1·2(pseudocode의 ping 200, data-model payload NULL 허용) 반영 → S07(GitHub App 연결, 선행 S04·S06 완료)
- 막힌 것: 없음. 실제 GitHub 전달 확인은 H-02. 시크릿은 환경 변수 `GITHUB_WEBHOOK_SECRET`(필수)·`GITHUB_WEBHOOK_SECRET_PREVIOUS`(교체 중)
- 참고: S07·S08 처리기는 `GithubEventHandler` 빈 하나만 더하면 된다(이벤트당 하나, 예외 = FAILED, GitHub API 금지). `last_error`에는 예외 종류만 남으니 DB에서 볼 설명은 `WebhookHandlingException`(개인정보 금지)으로. `spring.jpa.open-in-view`가 켜져 있어 요청 안의 새 트랜잭션은 같은 EntityManager·연결을 이어 씀(롤백 때 clear). DB 장애 테스트는 `github.WebhookTestConfiguration.DatabaseOutage`. 작업 프로세스가 재시작되면 dockerd가 죽어 있을 수 있음(`CLAUDE_CODE_REMOTE=true bash scripts/cloud-session-setup.sh`)
- PR: #12

## 2026-11-23 S05 스터디·규칙·기수·초대·멤버십 (세션 1)
- 한 일: 만들 것 1·2·3·5·6·7·8·9. V3 마이그레이션(studies, study_rules, seasons, memberships, invitations), 만들기(규칙 v1·1기·방장 한 트랜잭션), 목록(PagedModel)·상세, 이름 변경·위임, 초대 만들기·철회·미리보기(공개)·수락, 탈퇴, `StudyAccess`(비멤버 404, 비방장 403). 정원 동시성은 스터디 행 잠금 + 조건부 UPDATE(철회·만료도 다시 봄), 결정 제안서 006. `StudyCalendar`에 `nextStudyDayOf`·`seasonEndDay` 추가
- 상태: 빌드 통과 (테스트 322개). 구현 전 새 테스트 73개 중 72개 실패 확인. 잠금·UPDATE 조건을 하나씩 빼면 해당 테스트가 실패하는 것도 확인. spec-reviewer 1차 12건·2차 11건 처리(PR 본문)
- 사람이 정한 것 (2026-11-23, 세션 중 선택지): 시작일 "다음 날~30일 뒤 (추천)", 초대 철회 "DELETE 엔드포인트 추가 (추천)". 선택지 설명 원문과 에이전트가 해석한 부분은 PR 본문에 나눠 적음
- 다음: PR 검토 → 사람이 '지금 병합할지, OD-08까지 열어 둘지'(PR 열린 질문 1)와 결정 제안서 006을 정함 → 병합 뒤 `docs/` 문서 전용 PR로 두 결정과 받아들인 스펙 변경 제안을 반영 → OD-08이 정해지면 규칙 변경 `PUT /rules`·T11
- 막힌 것: 결정 대기 OD-08 (규칙 변경 API, T11). OD-07은 1기 생성만 했고 다음 기수는 S12. 단계 상태는 '결정 대기'
- 참고: S05 테스트는 `study/StudyTestSupport`(사용자는 DB에 넣고 `authentication()`으로 로그인, CSRF는 쿠키로)를 상속. 기수 SCHEDULED→ACTIVE 전환은 아직 어느 단계에도 없음(PR 열린 질문). Hibernate 7.4의 PostgreSQL 쓰기 잠금은 `FOR NO KEY UPDATE`
- 리뷰 반영 (같은 세션): CodeRabbit 2건. 철회 저장이 동시 수락의 `used_count`를 덮어쓰던 것(`updatable = false`), 소진된 초대는 정원 찬 스터디에서도 410. 새 테스트 2개 실패 확인 뒤 고침
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
- 요약: PR #1 CodeRabbit 4건 반영(반환값 검증 실패 500, 객체 오류 `field` null, 요청 이름 그대로, `docker info` 시간 제한), H-00 완료 확인, 결정 001 확정 기록. 빌드 통과(64개). PR: #1

## 2026-11-21 S01 공통 기반 (세션 1/1)
- 요약: 모듈 패키지·ArchUnit, `Clock` 주입·`now()` 금지, `@IntegrationTest`·`MutableClock`, problem+json, Actuator health만, springdoc 기본 꺼짐, 세션 준비 스크립트, 결정 제안서 001. 빌드 통과(61개). PR: #1

## 2026-11-21 하네스 문서 추가 (사람)
- 요약: CLAUDE.md, docs/(spec, steps, decisions, workflow), .claude/(훅, spec-reviewer), PR 템플릿

## 2026-11-21 S00 첫 커밋 (에이전트, master 직접)
- 요약: Gradle 9.9.2 wrapper, Spring Boot 4.1.3·Java 25, Testcontainers(`postgres:17`) contextLoads, CI(`build` 잡), 최소 README. JDK 25·dockerd 명령은 첫 세션 보고(H-09)
