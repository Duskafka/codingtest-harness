# 진행 기록

세션을 끝낼 때 맨 위에 더해요. 양식은 `docs/workflow.md` 7장에 있어요. 최신 기록이 위에 와요.

## 2026-11-26 S11 수동 인증과 면제권 (세션 1/1)
- 한 일: 만들 것 1~6. V9 `self_reports`. 수동 인증 `POST·DELETE /api/studies/{id}/self-reports`(verification `SelfReportService`, 풀이 기록 SELF_REPORT → progress 포트 `SelfReportCounting`가 그 스터디의 고른 날 멤버 일을 잠그고 센다, 하루 5건은 잠근 뒤 셈, 철회하면 WITHDRAWN·풀이 기록 IGNORED). 면제권 `POST /exemptions`·`DELETE /exemptions/{studyDay}`(progress `ExemptionService`, 오늘만, 멤버십 행 → 멤버 일 행 잠금, 기수 기간에서 SUCCESS가 아닌 사용을 셈). 현황 `members[]`에 `exemptionsRemaining`, 방장에게만 `selfReportRatio`(조회 포트 `SelfReportCounts`). study `StudyAccess#lockMember`, `MemberStudies#rulesOf`(`MemberStudyRules`), `MemberStudy#seasonOn`, `StudyDayContext`에 기수 기간·기수당 면제권
- 상태: 처음 PR을 연 때 빌드 통과 (테스트 716개). 구현 전 새 테스트 61개 중 58개 실패(통과 3개는 엔드포인트가 없어도 404인 T19). 조건 빼 보기 21개 모두 해당 테스트 실패(처음에 1개가 빠져 '마지막 면제권을 쓴 날 다시 써도 200' 테스트를 더함). spec-reviewer '수정 필요' → 필수 1(V9의 상태·결정 시각 CHECK가 승인 흐름을 막음, CHECK 뺌)과 고려 6건 반영(`solve_record_id` NULL 허용, 철회의 판정된 날 409는 `SELF_REPORT_DAY_JUDGED`, 나머지는 PR 본문)
- 사람이 정한 것 (2026-11-26, 지시 "S11 시작해 OD-9부터 결정해야해. 선택지와 장단점을 제시하면 선택할게", 세션 선택지, 모두 Recommended): OD-09 "당일만"(docs PR #28), 브랜치 "docs PR 먼저 + feat/S11", 수동 인증 범위 "그 스터디의 고른 날만", '승인 필요' 모드 "409로 거절", 수동 인증 비율 "이번 기수"
- PR 열린 질문 결정 (2026-11-26, 지시 "열린 질문 선택지와 장단점 알려줘 선택할게", 세션 선택지, 모두 Recommended): 유예 중 전날 수동 인증 "R4 전날 조건 적용"(잠근 전날 행으로 `AssignmentPlan.decide`, 아니면 409 `PREVIOUS_DAY_NOT_OPEN`), 철회 "다른 풀이로 바꿔 끼움"(그 스터디 일에 받은 같은 문제의 CONFIRMED·수동 인증 아님·이 스터디에서 아직 세지 않은 풀이로 집계 행을 바꿈, 조회는 `SolveRecordDetails#countableSolveIds`), 하루 5건 "철회도 셈", 남은 면제권 "모두에게"(뒤 둘은 지금대로). 결정 테스트 48개 중 5개 실패 확인 뒤 구현. 새 조건 빼 보기 9개 모두 걸림. spec-reviewer 2차 '통과'(고려 6건 반영: 바꿔 끼움 조건의 사람·에이전트 구분, 다음 날 유예 push 빈 곳을 열린 질문 5로, 받은 시각 순서·IGNORED 테스트, 409 문구, 이미 센 풀이 조회에 user_id). 빌드 통과 (테스트 725개)
- 열린 질문 5 결정 (2026-11-26, 지시 "열린 질문 5번도 선택지와 장단점", 세션 선택지 Recommended): "그런 push가 있으면 철회를 막음". 같은 날 push로 바꿔 끼울 수 없고 같은 문제 push(수동 인증 아님)를 다음 날 유예 안 `[startOf(D+1), graceEndOf(D))`에 받았으면 철회 409 `SAME_PROBLEM_PUSHED_IN_GRACE`. 테스트 53개 중 2개 실패 확인 뒤 구현, 조건 빼 보기 4개 모두 걸림(1개는 처음에 1ns 변형이라 DB 정밀도에 묻혀 안 걸려 1초로 다시 확인, 그 경계 테스트를 00:00으로). 빌드 통과 (테스트 728개)
- 다음: PR 검토 → 병합(#28과 S11 단계 문서가 겹치면 master 병합) → 받아들인 스펙 변경 제안을 docs PR로(R7·5.6 범위, 승인 모드 409와 승인 API 제안, 비율 범위, api.md 요청·응답·`code`·남은 면제권 모두에게, data-model `self_reports` 메모·하루 5건 철회 포함, R7 유예 중 전날 R4 조건, R7·5.6 철회 때 바꿔 끼움·다음 날 유예 push면 철회 막음) → S12(판정 배치) 또는 S13
- 막힌 것: 없음
- 참고: S12는 면제권을 쓴 날 목표를 채우면 SUCCESS로 판정하면 된다. 남은 개수 계산이 `result IS DISTINCT FROM 'SUCCESS'`라 판정 결과만 바꾸면 면제권이 돌아온다. 잠금 순서는 이렇다: 면제권은 멤버십 → 멤버 일, 수동 인증 철회는 수동 인증 → 풀이 기록 → 멤버 일, 제출은 멤버 일 하나만. 판정은 멤버 일만 잠그니 순환이 없다. S13의 알림 대상 제외는 `exemption_used_at`으로 본다. 처음 열린 질문 넷은 모두 정함(위). 병합 순서: #28 → master를 이 브랜치에 병합 → #28이 더한 S11 수용 기준 줄 체크. 세션 재시작 뒤 dockerd가 오래된 pid로 안 뜨면 `CLAUDE_CODE_REMOTE=true bash scripts/cloud-session-setup.sh`
- PR: #29

## 2026-11-26 S10 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #26(병합)의 스펙 변경 제안 다섯 개를 문서에 반영. R6(늦은 지정의 '그날' = 원래 수신 시각으로 R4를 적용해 배정할 날, 유예 안 전날 실패면 이의, 한 스터디라도 그러면 아무 데도 반영 안 함, '판정됐다' = 그 멤버의 그날 판정 결과), api.md 4.4(표 `?from=&to=&status=`, '내 풀이' 항목, 오류 `code` 규칙과 지금 값 세 개, "Spring Boot 3" → "Spring"), glossary(`MemberDayStatus`, `ExemptReason`), S10 만들 것 3·4
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것: 2026-11-25 S10 세션 선택지 "전부 409, 아무것도 안 바꿈", "FAIL이면 409". 2026-11-26 지시 "열린 질문을 결정해보자 선택지와 각각의 장단점을 가져와 내가 선택할게" 뒤 선택지 "status 필터 추가", "code 속성 추가", "멤버 일 결과 그대로". 2026-11-26 지시 "26 병합했어" 뒤 선택 제안 질문에 "glossary에 상태 이름 추가", "api.md 'Spring Boot 3' 문구 정정" (모두 Recommended)
- 다음: 이 PR 병합 → S11(수동 인증과 면제권, 결정 게이트 OD-09 확인부터)
- 막힌 것: 없음
- 참고: 에이전트가 덧붙인 문장(PR 본문에 표시): R6의 '결과가 없는 날은 그날에 기록'(가입한 날까지는 S09 PR #24 "지금대로 그날에 기록", 기수 밖은 2026-11-26 "멤버 일 결과 그대로"의 설명), api.md의 '`type`은 `about:blank` 그대로'와 '모르는 값은 400'. 빈 `status=`(거르지 않음)와 대소문자 구분, 규칙 변경 409 두 가지의 `code`는 스펙에 넣지 않았다(PR 열린 질문). spec-reviewer 필수 1(R6에 'R4의 전날 후보' 조건) 반영
- PR: #27

## 2026-11-25 S10 오늘 현황·내 풀이·확인 대기 지정 (세션 1/1)
- 한 일: 만들 것 1~5. `GET /api/studies/{id}/today`·`/days/{date}`(progress: 그날 멤버였던 사람의 DONE/REMAINING/EXEMPT/NOT_JUDGED, 판정 결과, 최근 배정 50개를 수신 시각 순서로, 출처 = 수동 표시 자리), `GET /api/me/solves`(확인 대기 포함, from·to 오프셋 있는 ISO 8601, 크기 50까지), `PATCH /api/me/solves/{id}`(verification: 내 확인 대기 잠금 → 문제 채움 → `StudyDayAssignment#assignIdentified`). study `StudyDays`·`StudyDayContext`, auth `UserDirectory#profilesOf`, progress 조회 포트 `SolveRecordDetails`(verification 구현), `RuleVersionCalendar#graceEndOf`. 테이블 변경 없음
- 상태: 빌드 통과 (테스트 651개). 구현 전 새 테스트 52개 중 49개 실패(통과 3개는 엔드포인트가 없어도 404인 T19, 조건 빼 보기로 확인). 조건 빼 보기 16개 모두 해당 테스트 실패(2개는 처음에 빠져 테스트를 더함). `/today`는 멤버 3명·30명 모두 9문장. 측정(18만 행): p50 11.5ms·p95 19.4ms, 주요 쿼리는 인덱스 사용. spec-reviewer '수정 필요'(코드 위반 없음, 필수 1: 아래 R6 해석) → 사람에게 물어 반영, 고려 11건 중 6건 코드·설명 반영, 나머지는 PR 판단 기록·열린 질문
- 사람이 정한 것 (2026-11-25, 지시 "S10 시작해", 세션 선택지, 모두 Recommended): 브랜치 "feat/S10-today-status", 일부 스터디만 판정된 날 "전부 409, 아무것도 안 바꿈", 최근 풀이 "조회 포트". spec-reviewer 뒤: 유예 안 확인 대기의 전날이 이미 FAIL "FAIL이면 409"(받은 시각에는 전날로 갔을 풀이, R6. 전날이 성공·면제면 받은 날로)
- PR 열린 질문 결정 (2026-11-26, 지시 "열린 질문을 결정해보자 선택지와 각각의 장단점을 가져와 내가 선택할게", 세션 선택지, 모두 Recommended): 상태 필터 "status 필터 추가", 409 구분 "code 속성 추가", '판정됐다' 기준 "멤버 일 결과 그대로", 가입 전 풀이 "지금대로 둠". 앞의 둘은 구현(`GET /api/me/solves?status=`, 409 `code`: STUDY_DAY_JUDGED·SOLVE_NOT_PENDING·PROBLEM_ALREADY_IN_COMMIT, `ConflictException(code, detail)`). CodeRabbit 2건도 반영: 동시 지정의 UNIQUE 위반 → 409(`pg_stat_activity`로 결정적 재현), 현황 조회 REPEATABLE_READ. 새 테스트 8개 실패 확인 뒤 구현, 조건 빼 보기 3개 모두 걸림, 빌드 통과(654개)
- 다음: PR 검토 → 병합 → 받아들인 스펙 변경 제안을 docs PR로(R6 늦은 지정의 '그날'·'판정됐다' 기준, api.md `status` 파라미터와 오류 `code` 규칙, glossary `MemberDayStatus`·`ExemptReason`) → S11(수동 인증과 면제권, OD-09)
- 막힌 것: 없음
- 참고: S11·S12에 넘길 것 — 확인 대기 지정은 풀이 기록 행 → 멤버 일 행들(007 전역 순서) 순서로 잠근다. 풀이 기록과 멤버 일을 함께 잠그는 흐름(수동 인증 철회 등)은 같은 순서를 따라야 교착이 없다. 409의 '판정됐다'는 그 사용자 멤버 일의 `result`로 본다(가입 전·기수 밖 날은 결과가 없어 409 없이 기록, 판정에 영향 없음, 사람이 정함 2026-11-26). S12가 `last_judged_day`를 만들어도 이 기준은 그대로다. 현황 `status`는 지금 센 수로 정하고 판정 결과는 `result`로 따로 준다(S12가 금액 표시를 더할 자리). 화면이 갈라야 하는 오류에는 problem+json `code`를 둔다(`ConflictException(code, detail)`, 대문자 스네이크). S11·S12의 새 409도 화면이 가를 필요가 있으면 이 방식을 쓴다. 웹훅 테스트의 배정 대역은 익명 클래스가 됐다. 새 테스트는 `TestApi`(로그인·CSRF 요청 도우미)와 `StudyDayRows`(githubSolve·pendingSolve·markJudged)를 쓴다
- PR: #26

## 2026-11-25 S09 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #24(병합)의 스펙 변경 제안 1~3과 결정을 문서에 반영. 5.5(흐름도: 판정 대상·쉬는 날을 잠그기 전에, 잠금 순서 (스터디 일, 스터디 ID), 한 트랜잭션의 풀이는 같은 인정 시각, 배정할 때의 멤버십만), 부록 D(`planStudyDay`·`assignStudyDay`·`recordSolve`), data-model '멤버 일 테이블 (S09)' 메모(CHECK, NULL 허용 FK, 판정하지 않는 날의 행), R4·R14(배정할 때의 멤버십, 탈퇴 뒤 다시 가입하기 전까지는 세지 않음, 같은 날 다시 가입하면 다시 셈, 전날 판정 대상도 배정할 때의 가입일), Q2(유예 안 같은 문제도 함께 정함), 결정 대장(제안서 007, ADR-006 행), S09 만들 것 1·2·읽을 문서, S10 주의할 것·읽을 문서(R14), S12 읽을 문서(007, R14)·주의할 것(같은 날 재가입)·판단 기록(새 제안서). PR 열린 질문 두 개도 반영: 3.4 멤버 일 진입 문구('첫 풀이' → '풀이 배정'), '판정 대상'을 R9 뜻으로 맞춤(R4 세 번째 조건, S09 만들 것 3)
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎). spec-reviewer 1차 '수정 필요'(필수 2: Q2 서술 조건 빠짐, 같은 날 재가입이 새 R14 문장과 부딪힘) → Q2 고치고 재가입·탈퇴 기준·S12 제안서를 사람에게 물어 반영. 2차 '수정 필요'(필수 1: 재가입한 멤버의 다음 날 유예 안 풀이를 R4·R9는 탈퇴한 날로, 코드는 다음 날로) → 사람에게 물어 반영, 고려 4건 반영
- 사람이 정한 것 (2026-11-25, 지시 "내가 정해야 할 것의 선택지와 그 선택지의 장단점을 비교해서 제시하면 내가 선택할게", 세션 선택지, 모두 Recommended): 스펙 변경 제안 "1 잠금 순서 (5.5, 부록 D)", "2 V8 제약 메모 (data-model)", "3 탈퇴 문구 (R4·R14)", 007 "A 행 잠금 + 전역 순서", 유예 안 같은 문제 "지금대로, Q2와 함께 정함", 가입 전 인정 시각 "지금대로 그날에 기록". 앞선 선택 "활성 멤버십만". docs PR 검토 뒤: 같은 날 재가입 "다시 가입하면 다시 셈", 탈퇴 기준 "배정할 때의 멤버십", S12 제안서 "새 제안서 008로". 2차 검토 뒤: 재가입한 멤버의 유예 "코드대로 D+1". PR 열린 질문: 지시 "1. 조건 문구를 바꾼다면 어떻게 바꿔야해" → "'풀이 배정'으로", 지시 "2.이건 일치시켜"(선택지 없는 지시. 맞추는 방향 R9 뜻은 에이전트가 제안하고 사람이 계획 승인으로 확인)
- 다음: 이 PR 병합 → S10 또는 S11 (선행 S09 완료)
- 막힌 것: 없음
- 참고: 가입 전 인정 시각을 물을 때 에이전트가 붙인 설명 "판정에 영향 없다"는 같은 스터디에 다시 가입한 사람에게는 틀렸다(옛 멤버십으로 판정하는 날일 수 있음). 사람에게 알리고 '배정할 때의 멤버십'을 따로 물어 정함. S10·S12·Q2에 더한 줄은 결정을 다음 단계에 전하는 문장이다 (PR 본문에 표시). 결정 제안서 007의 '판정 대상(쉬는 날·면제권 아님)'(14행)과 '(가입·기수·쉬는 날)'(51행)은 확정된 기록이라 고치지 않음
- PR: #25

## 2026-11-25 S09 스터디 일 배정과 멤버 일 (세션 1/1)
- 한 일: 만들 것 1~6. V8(`member_days`, `study_day_solves`), study 공개 API `MemberStudies`·`MemberStudy`(활성 멤버십, 버전 달력, 버전별 목표, 기수 기간, R9 판정 대상), progress `AssignmentPlan`(R4 계획·결정), `StudyDayAssigner`(S08 배정 연결 지점의 실제 구현, `PendingStudyDayAssignment` 삭제). 잠글 행(후보 전날 + 오늘)을 모든 스터디에 걸쳐 (스터디 일, 스터디 ID) 순서로 `INSERT ON CONFLICT` → `SELECT FOR UPDATE`(네이티브) 뒤 결정, `study_day_solves` 넣으면 센 수를 다시 셈. 결정 제안서 007(기록용, 부록 D 순서의 교착 시나리오)
- 상태: 빌드 통과 (테스트 592개). 구현 전 새 테스트 38개 모두 실패 확인. 조건 빼 보기 10개 모두 해당 테스트 실패(FOR UPDATE를 빼면 '전날 행이 이미 있을 때' T7만 잡혀 그 테스트를 더함). spec-reviewer '수정 필요'(코드 지적 없음, 필수 3건은 PR 본문·인계 기록) → 반영
- 사람이 정한 것 (2026-11-25, 세션 선택지, 모두 Recommended): 브랜치 "feat/S09-study-day-assignment", 탈퇴 "활성 멤버십만"(탈퇴한 뒤 들어온 풀이는 탈퇴한 날을 포함해 어느 날에도 세지 않음)
- PR 열린 질문 결정 (2026-11-25, 지시 "내가 정해야 할 것의 선택지와 그 선택지의 장단점을 비교해서 제시하면 내가 선택할게", 세션 선택지, 모두 Recommended): 유예 안 같은 문제 "지금대로, Q2와 함께 정함", 결정 제안서 007 "A 행 잠금 + 전역 순서"(007 '결정'에 받아 적음, 상태 확정), S10 확인 대기의 가입 전 인정 시각 "지금대로 그날에 기록", 스펙 변경 제안 "1 잠금 순서 (5.5, 부록 D)", "2 V8 제약 메모 (data-model)", "3 탈퇴 문구 (R4·R14)" 모두 반영
- 다음: PR 검토 → 병합 → docs PR: 스펙 변경 제안 1~3과 결정 대장(제안서 목록에 007, ADR-006 행) 반영 → S10 또는 S11
- 막힌 것: 없음
- 참고: S10·S11·S12는 멤버 일을 `progress.persistence.MemberDayRepository`(네이티브 `insertIfAbsent`·`lock`·`recountSolved`)로 다룬다. 판정(S12)은 한 트랜잭션에 행 하나만 잠그거나 날짜 오름차순으로(007). 판정하지 않는 날(가입한 날, 1기 전)에도 행이 있을 수 있고 `season_id`가 NULL일 수 있다. S12가 `rule_id`·`result_source`·`judged_at`을 채운다. S15는 `study_day_solves.solve_record_id` FK 때문에 풀이 기록보다 먼저 지워야 한다. 열린 질문: 유예 안 같은 문제(덜 세거나 이틀을 채움, Q2와 이어짐), S10 확인 대기 지정의 가입 전 인정 시각. 첫 빌드는 Maven Central 429로 두 번 실패, 다시 돌려 통과
- PR: #24

## 2026-11-25 PR #21 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #21(병합)의 스펙 변경 제안 1~6을 문서에 반영. api.md 4.4(표 세 줄, '규칙 변경' 메모: `basedOnRuleId` + 규칙 전체, 400·409, 권한 먼저, `rule`·`upcomingRules[]`의 버전 `id`, 미리보기), R2(1기 시작 전 변경), R4·R9·5.10·5.5(유예 구간·판정 시각은 D+1(= N) 규칙의 유예), 5.8 REMINDER 시점, S09 만들 것 1, S12 만들 것 2, S13 만들 것 2(알림 시각), S16 화면 4·9·읽을 문서, product 화면 4. spec-reviewer 지적으로 시간 계산을 `StudyCalendar` 하나로 적은 곳(5.5, architecture 4.2·6장, glossary)과 부록 D `assignStudyDay`·알림 생성도 버전 달력에 맞춤
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎). spec-reviewer '수정 필요' → 필수 2건·선택 6건 반영, 2건은 PR 열린 질문(보증금액, 수동 인증의 유예)
- 사람이 정한 것 (2026-11-25, 지시 "다음 스펙 변경 제안 pr을 하자", 세션 선택지, 모두 Recommended): 반영 범위 "프론트 계약: api.md와 S16", "R2 1기 시작 전 변경", "R9 문구 명확화", "S09·S12 버전 달력", 알림 시각 "그날(D)의 규칙"(PR #21 열린 질문 1). 나머지 근거는 S05 세션 결정(2026-11-24, 모두 Recommended): "규칙 전체를 받음", "현재 규칙 + 예정된 변경", "예정 변경도 보여 줌", "기준 버전 ID를 받음", "모두 1기 시작일부터", "거절 (409)", "바꾼 뒤의 유예", "PR #21 달력에 더함", "S05에서 스터디 일까지"
- 다음: 이 PR 병합 → S09
- 막힌 것: 없음
- 참고: 그대로 남은 열린 질문: PR #21 3(변경 이력)·4(보증금액)·5(늦은 시각의 유예 변경), PR #20 6·7. `RuleVersionCalendar` 클래스 주석('알림 시각은 정하지 않았다')은 S13 코드 PR에서 `reminderAt`을 더할 때 고친다
- PR: #23

## 2026-11-24 다음 기수 행을 만드는 시점 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #20 열린 질문 1(다음 기수 행을 언제 만드나)과 PR #11 열린 질문(시작일의 SCHEDULED→ACTIVE 전환을 맡은 단계 없음)의 결정을 문서에 반영. R13·3.4 기수 상태도, 5.13(만들 것·흐름도·주의할 것), 5.10 처리 순서·흐름도, pseudocode 판정 배치 `startSeasons`, data-model `seasons` 비고, S12 목표·만들 것 6·수용 기준 3개, S09 만들 것 3
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎). spec-reviewer '수정 필요' → 필수 3건(R13 범위 좁힘, 상태값 대신 날짜로 판단, 근거 없는 잠금 방식 뺌)과 선택 9건을 문서에 반영, 나머지 2건(모듈 의존, 잠금 방식)은 PR 열린 질문 → 2026-11-25 사람이 정해 반영
- 사람이 정한 것 (2026-11-24, 질문 "다음 기수 행은 언제 만드는 게 좋을까", 세션 선택지, 모두 Recommended): 생성 시점 "이번 기수가 시작될 때"(늘 한 기수 앞섬), 맡을 단계 "S12 판정 배치가 함께"(판정 전에 ACTIVE 전환과 다음 행 생성), 반영 "지금 별도 docs PR"
- PR 열린 질문 결정 (2026-11-25, 지시 "열린 질문을 처리하자 각 대안의 장단점을 가져와서 나에게 제시해 내가 선택할게", 세션 선택지, 모두 Recommended): 모듈 의존 "judgment → study 화살표 추가"(architecture 4.2, 알려진 충돌 11번), 직렬화 "스터디 행 잠금 + 006 확정"(결정 제안서 006 확정, 범위를 규칙 변경·기수 시작 처리까지, 기수 시작 처리는 판정과 따로 짧은 트랜잭션), 반영 "PR #22에 더함". 006 확정에 맞춰 5.1·S05 '정원 동시성'(스터디 행 잠금 + 조건부 UPDATE), 5.10 처리 순서 4, S15 읽을 문서도 고침
- 다음: 이 PR 병합 → PR #21의 스펙 변경 제안 docs PR → S09. PR #21이 먼저 병합돼서(2026-11-25, 사람이 "21 22" 순서를 묻고 #21을 병합) master를 이 브랜치에 병합하고 이 파일 충돌을 풀었다(두 기록 모두 남김)
- 막힌 것: 없음
- 참고: 에이전트가 도출해 문서에 적은 것(사람 확인 필요, PR 본문에 표시): 기수 중 길이 변경·취소 때 예정 행 끝 날짜 다시 세기(PR #21 열린 질문 2), 예정 행이 있어도 적용 기수는 그대로(PR #20 열린 질문 4 뒷부분), 시작 여부·기수 안·이번 기수는 날짜로 판단, 따라잡기 때 되풀이. S12 코드 PR에서 `ModuleRules` 허용 목록에 judgment → study를 더한다. S12 코드 PR에서 고칠 주석: `SeasonStatus`, `Season` 생성자(옛 3.4 라벨), `StudyTimeline.seasonOn`·`nextSeasonStart`. `V3__create_study_tables.sql` 주석도 맞지 않게 되지만 master 마이그레이션이라 고치지 않는다
- PR: #22

## 2026-11-24 S05 남은 일: 규칙 변경 API와 T11 (세션 2)
- 한 일: 만들 것 4. `PUT /api/studies/{id}/rules`(`StudyRuleService`), 버전에 걸친 스터디 일 계산 `common.time.RuleVersionCalendar`(기준 시각이 바뀌는 새 기수 첫날 18~30시간), 상세 응답 `upcomingRules`, `StudyTimeline.currentRule`(버전이 둘이면 예외)을 날짜로 고르는 방식으로. 테이블 변경 없음. PR을 연 뒤: `basedOnRuleId` 기준 버전 검사(409), 버전 달력의 `isInGrace`·`judgeAt`(바뀐 뒤의 유예)
- 상태: 빌드 통과 (테스트 553개). 구현 전 새 테스트 29개 중 28개 실패 확인. 조건 빼 보기 5개 모두 해당 테스트 실패. spec-reviewer '수정 필요' → 테스트 13개 더함(T11 열 항목 분류, 기수 이어 세기, 미리보기, 409), 그중 2개 실패 확인 뒤 고침. PR을 연 뒤 보강 테스트 11개 모두 실패 확인 뒤 구현, 조건 빼 보기 3개 모두 걸림
- 사람이 정한 것 (2026-11-24, 세션 선택지, 모두 Recommended): 날 계산 "S05에서 스터디 일까지", 1기 시작 전 변경 "모두 1기 시작일부터", 상세 "현재 규칙 + 예정된 변경", 요청 "규칙 전체를 받음". spec-reviewer 뒤: 공개 미리보기 "예정 변경도 보여 줌", 시작 직전 기준 시각을 앞당겨 첫날이 이미 시작되면 "거절 (409)". PR을 연 뒤 장단점 비교로: PUT 기준 "기준 버전 ID를 받음", 유예가 바뀐 날 "바꾼 뒤의 유예"(PR #20 열린 질문 3), 구현 위치 "PR #21 달력에 더함"
- 다음: PR 검토 → 병합 → 받아들인 스펙 변경 제안을 docs PR로(api.md `upcomingRules`·`basedOnRuleId`·규칙 `id`, R2 시작 전 변경, R9 '유예'를 'D+1 규칙의 유예'로 적는 문구 명확화(부록 D와 같아 규칙 변경은 아님), S09·S12·S13·S16 문서) → S09. S09 전에 PR #20 열린 질문 1(다음 기수 행 생성 시점)을 정하면 좋음
- 막힌 것: 없음
- 참고: S09·S12는 스터디 일·유예 구간·판정 시각을 `RuleVersionCalendar`로 센다(유예는 구간이 시작되는 날의 규칙). 알림 시각은 버전에 걸쳐 정하지 않았다(S13). 화면은 마지막 버전의 `id`를 `basedOnRuleId`로 보낸다. 세션 재시작 뒤 dockerd가 죽어 있으면 `CLAUDE_CODE_REMOTE=true bash scripts/cloud-session-setup.sh`
- PR: #21

## 2026-11-24 OD-07·OD-08 결정 반영 (docs PR, 단계 진행 아님)
- 한 일: 사람이 정한 OD-07·OD-08을 결정 대장(확정), 스펙(R2·R9·R13, glossary `JudgmentResult`, 5.13, api `PUT /rules`, testing T11, product 기능 표), 단계 문서(S05 만들 것 4·T11·판단 기록, S09 판정 대상, S12 만들 것 6·수용 기준, steps README의 S05 상태 '대기')에 반영
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것 (2026-11-24, 세션 선택지, 모두 Recommended): 다음 기수 "마지막 날 다음 날 바로", 기수 밖 날 "판정 대상에서 뺀", 규칙 변경 "일부는 다음 기수부터"(기준 시각·기수 길이·기수당 면제권·벌금), 기준 시각이 바뀌는 경계의 길이가 달라지는 하루(18~30시간) "새 기수 첫날"
- 다음: 이 PR 병합 → S05 남은 일(규칙 변경 API `PUT /rules`, T11) → S09. docs PR #19가 먼저 병합돼서(사람이 정함 "#19 먼저") master를 이 브랜치에 병합하고 이 파일 충돌을 풀었다
- 막힌 것: 없음
- 참고: spec-reviewer가 찾은 세부 질문 8개(다음 기수 행을 만드는 시점, 새 기수 첫날 계산을 맡을 단계, 유예가 바뀌는 날, 1기 시작 전 변경의 적용 기수 등)는 PR 열린 질문. S05 남은 PR은 `StudyTimeline.currentRule`(규칙 버전이 둘이면 예외)부터 손봐야 함
- PR: #20

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
- 요약: V3(studies, study_rules, seasons, memberships, invitations), 만들기·목록·상세·이름 변경·위임, 초대 만들기·철회·미리보기·수락, 탈퇴, `StudyAccess`(비멤버 404, 비방장 403). 정원 동시성은 스터디 행 잠금 + 조건부 UPDATE(결정 제안서 006). 사람이 정함: 시작일 "다음 날~30일 뒤", 초대 철회 "DELETE 엔드포인트 추가". 테스트는 `study/StudyTestSupport` 상속. Hibernate 7.4의 PostgreSQL 쓰기 잠금은 `FOR NO KEY UPDATE`. 빌드 통과(322개). PR: #11

## 2026-11-22 S04 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 요약: PR #9 제안 두 건 반영(architecture 4.1 Nginx 경로에 `/logout`·`/github/setup`·`/actuator/health`, data-model `users` github_id CHECK·deleted_at). 훅은 `docs/S[0-9]*` 브랜치를 단계 브랜치로 봐서 스펙 수정을 막으니 문서 전용 PR 브랜치는 `docs/S`로 시작하지 않게 한다. PR: #10

## 2026-11-22 S04 로그인과 세션 구현 (세션 1, 이어서)
- 요약: GitHub App 사용자 인증 로그인, DB 세션(`studybot.SPRING_SESSION`), `__Host-Http-SESSION` 쿠키 빈과 30일 롤링 필터, `csrf.spa()`, `/webhooks/**` 별도 체인, `GET /api/me`, `POST /logout`, 토큰 미저장. 공유 테스트 컨텍스트에서 `csrf()` 후처리기를 쓰지 말고 쿠키로 토큰을 받을 것. dockerd가 죽어 있으면 `CLAUDE_CODE_REMOTE=true bash scripts/cloud-session-setup.sh`. 빌드 통과(238개). PR: #9

## 2026-11-22 S04 로그인과 세션: OD-02 결정 제안서와 결정 반영 (세션 1)
- 요약: 결정 제안서 005(GitHub 로그인 방식), 사람이 B(GitHub App 사용자 인증)를 고름. 결정 대장·스펙·단계 문서에 반영, H-03은 H-02에 합침. 시작할 때 `git fetch origin master`부터 하기. PR: #8

## 2026-11-22 OD-01·OD-05·OD-06 결정 반영 (docs PR, 단계 진행 아님)
- 요약: 결정 대장(OD-05·06 확정, 제안서 003·004, D22)과 스펙(architecture '세션'·'Lombok', infra 9.4 `studybot`, data-model, api 4.5, security)·단계 문서(S04·S06·S17)에 반영. 문서만 바꿈. PR: #7

## 2026-11-22 하네스 변경: 사람이 정한 결정의 문서 반영을 에이전트에게 (단계 진행 아님)
- 요약: 사람 요청으로 결정 반영을 에이전트가 `docs/` 브랜치의 문서 전용 PR로 하게 규칙을 바꿈(workflow 2~5·9장, 문서 머리 안내, 루트 README). 훅·CLAUDE.md 등은 사람이 적용(`d529511`). 단계 브랜치(`feat/…`, `docs/S<번호>-…`)에서는 스펙·결정 대장을 못 고침. PR: #6

## 2026-11-22 OD-05·OD-06 결정 제안서 (S04·S05·S06 게이트 준비, 단계 진행 아님)
- 요약: 결정 제안서 003(Lombok, 사람이 정함: 허용 목록 + `@Builder`)과 004(스키마 `studybot`). Lombok 애너테이션은 SOURCE 보존이라 ArchUnit으로 못 봄. 빌드 통과(189개). PR: #5

## 2026-11-22 OD-01 결정 제안서 (S04 게이트 준비, 단계 진행 아님)
- 요약: 결정 제안서 002(로그인 유지). 사람이 정함: DB 세션, 30일 롤링만(추천과 다름), SameSite=Lax + `__Host-Http-`. 앱(Android·iOS)은 P1 이후 계획, 로그아웃·탈퇴는 즉시 무효화. PR: #4

## 2026-11-22 S03 문제 식별 파서 (세션 1/1)
- 요약: `verification.ProblemParser`(파서 버전 1), `ProblemRef`, `Platform`(BOJ·PROGRAMMERS·LEETCODE), 커밋 샘플 18개(모두 '추측', H-01). 백준허브 일괄 업로드는 결과 없음(사람이 정함). 빌드 통과(189개). PR: #3

## 2026-11-22 S02 시간 계산 (세션 1/1)
- 요약: `common.time`의 `StudyTimeRule`·`StudyCalendar`(`studyDayOf`, `boundaryOf`, `isInGrace`, `reminderAt`, `judgeAt`, `isRestDay`), 서머타임 경계 테스트. 다른 모듈은 그날의 규칙으로 `StudyTimeRule`을 만들어 쓴다. 빌드 통과(113개). PR: #2

## 2026-11-21 S01 공통 기반 리뷰 반영 (세션 2)
- 요약: PR #1 CodeRabbit 4건 반영(반환값 검증 실패 500, 객체 오류 `field` null, 요청 이름 그대로, `docker info` 시간 제한), H-00 완료 확인, 결정 001 확정 기록. 빌드 통과(64개). PR: #1

## 2026-11-21 S01 공통 기반 (세션 1/1)
- 요약: 모듈 패키지·ArchUnit, `Clock` 주입·`now()` 금지, `@IntegrationTest`·`MutableClock`, problem+json, Actuator health만, springdoc 기본 꺼짐, 세션 준비 스크립트, 결정 제안서 001. 빌드 통과(61개). PR: #1

## 2026-11-21 하네스 문서 추가 (사람)
- 요약: CLAUDE.md, docs/(spec, steps, decisions, workflow), .claude/(훅, spec-reviewer), PR 템플릿

## 2026-11-21 S00 첫 커밋 (에이전트, master 직접)
- 요약: Gradle 9.9.2 wrapper, Spring Boot 4.1.3·Java 25, Testcontainers(`postgres:17`) contextLoads, CI(`build` 잡), 최소 README. JDK 25·dockerd 명령은 첫 세션 보고(H-09)
