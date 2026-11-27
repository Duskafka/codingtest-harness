# 진행 기록

세션을 끝낼 때 맨 위에 더해요. 양식은 `docs/workflow.md` 7장에 있어요. 최신 기록이 위에 와요.

## 2026-11-27 S13 알림 기반과 웹 푸시 (세션 1/1)
- 한 일: 만들 것 1~7. 1(결정 제안서 009)은 docs PR #34. V11(`notifications`, `push_subscriptions`, S13 종류·채널만). `RuleVersionCalendar#reminderAt`(그날 규칙). study `ReminderDays`(지금 시각의 스터디 일, 진행 지점·알림 시각)·`StudyRulesChanged`(새 버전일 때 같은 트랜잭션에서 발행). progress `ReminderTargets`(현황과 같은 '남음' 계산 + 탈퇴한 멤버 제외, 발송 직전 재확인). 규칙 변경은 예정 구간 내용이 같으면 알리지 않음. notification `outbox`(dedupe_key, payload `{type,title,body,url}`), `reminder`(1분·ShedLock, 스터디마다 트랜잭션, SOLO는 공유 유도 없음 R15), `rule.RuleChangeNotifier`(같은 트랜잭션 리스너), `dispatch`(5초·SKIP LOCKED, 20개·잠금 2분, 잠금 만료 시각이 토큰, 백오프 30초×2^(n−1)·최대 30분, Retry-After), `webpush`(zerodep으로 VAPID·aes128gcm만, JDK HttpClient로 보냄, VAPID 키 기동 검사, 허용 도메인), 구독 API 3개, 지표, 스케줄러 스레드 4개
- 상태: 빌드 통과 (테스트 825개). 구현 전 새 테스트 53개 중 47개 실패(통과 6개는 아무것도 안 해도 참). 조건 빼 보기 28개 모두 해당 테스트 실패(같은 트랜잭션은 커밋 전에 보이는지 테스트를 더해 가름, 혼자 쓰기 테스트는 구현 뒤 더해 조건 빼 보기로 실패 확인). zerodep이 Java 25에서 만든 요청을 테스트가 JDK로 복호화·VAPID 서명 검증해 통과. spec-reviewer 1차·2차 '통과'(필수 없음) → 1차 고려 10건: 테스트 10개 더함(구현 전 6개 실패), 조건 빼 보기 9개 중 8개 걸림(마이크로초 자르기는 PostgreSQL JDBC가 같은 방식으로 맞춰 안 걸림, 방어로 남김), 나머지는 PR 판단 기록
- 사람이 정한 것 (2026-11-27, 지시 "S13(알림 기반과 웹 푸시)을 시작", 세션 선택지, 모두 Recommended): OD-11 흐름 "Proposal first, decide in-session", OD-11 "A. zerodep 2.1.5", RULE_CHANGED "Listener in the same transaction", spec-reviewer 뒤 그날 탈퇴한 멤버 "보내지 않음"(알림 시각·발송 직전에 탈퇴 상태면 빼거나 SKIPPED, 판정은 그대로)
- 다음: #34 병합 → 이 PR 검토(판단 기록 '꼭 봐 주세요': 구독 API·payload·RULE_CHANGED 방식·허용 도메인·VAPID 키·V11) → 병합(progress 충돌은 두 기록 모두 남김) → 받아들인 스펙 변경 제안을 docs PR로(4.2 같은 트랜잭션 리스너, data-model, api.md, 9.7 도메인, 5.8·pseudocode(탈퇴 제외 포함), infra 9.6 VAPID 형식·subject, glossary 채널, RULE_CHANGED는 내용이 바뀔 때만) → S14
- 막힌 것: 없음. 실제 기기 수신 확인은 H-04(운영 VAPID 키, `WEBPUSH_VAPID_SUBJECT` 포함)와 S16 뒤
- 참고: S14는 V11 CHECK와 `NotificationType`·`NotificationChannel`에 디스코드·결과 알림을 새 마이그레이션으로 더하고, `NotificationOutbox`에 디스코드용 넣기, `NotificationSender`(채널 DISCORD)를 구현하면 발송기가 그대로 보낸다(받는 사람 없는 알림은 멤버 재확인을 건너뜀). 결과 알림도 같은 트랜잭션 리스너로 하면 알려진 충돌 2번이 같은 방식(PR 열린 질문). S15는 `notifications`·`push_subscriptions`를 사용자보다 먼저 지워야 한다(FK). 발송기 테스트는 `NotificationRows#expireLeftovers`로 다른 테스트의 발송 대기 행을 치운다. `@IntegrationTest` 밖에서 앱을 띄우는 테스트는 VAPID 테스트 키를 넣어야 뜬다(`WebPushTestKeys`)
- PR: (이 PR)

## 2026-11-27 S12 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #32(병합)의 결정과 받아들인 스펙 변경 제안을 문서에 반영. 쿼리 수(S12 수용 기준, 5.10 '주의할 것', architecture 6장 ⑤: 조회는 스터디·날짜마다 한 번, 멤버마다는 잠금·저장), 정산 72시간(R13, 3.4 기수 상태, 5.13 흐름도·주의할 것, data-model `seasons.settling_started_at`), 5.13 알려진 한계(기수 하나보다 긴 장애 뒤 배치 전 구간), data-model 멤버 일(`result_source` = `BATCH`, 비어 있던 `season_id`를 판정이 채움), api.md 4.4 '현황의 정산 표시'(`penaltyType`·`fineAmount`·`failCount`·`fineTotal`), 알려진 충돌 9번 다룸·11번 ModuleRules 반영
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것: S12 세션(2026-11-27, 지시 "S12 시작해") "조회만 일정, 멤버별 잠금", "실제 판정 시각 + 72시간", "모든 멤버에게 멤버마다". PR #32 열린 질문 1(지시 "PR의 열린 질문 장단점을 비교해서 제시해 선택할게") "A. 지금대로 둠". 2026-11-27 지시 "병합했고 다음엔 뭘 하면 돼?" 뒤 세션 선택지: 순서 "docs PR → S13"(Recommended), 반영 범위 네 개 모두 "쿼리 수·알려진 한계", "정산 72시간", "result_source·season_id", "현황 응답 필드·충돌 9·11"(여러 개 고르는 질문, 추천 표시 없음), CI 캐시 "S17에서 다룸"(Recommended). spec-reviewer '통과'(필수 없음), 고려 4건 반영(알려진 한계 문구를 코드에 맞게 좁힘, R13에 예외 참조, 쿼리 수 문구 세 곳 맞춤, data-model 문구 다듬음)
- 다음: 이 PR 병합 → 새 세션에서 S13(알림 기반과 웹 푸시, 결정 게이트 OD-11: 에이전트가 웹 푸시 라이브러리 결정 제안서를 먼저 씀, 1~5번은 진행 가능). '한 세션에 단계 하나'라 S13은 이 세션에서 하지 않음
- 막힌 것: 없음
- 참고: 결정 제안서 008은 '제안'이라 결정 대장 목록·ADR-006 행은 고치지 않았다(사람이 정할 때 반영). CI가 Maven Central 429로 실패하면 실패한 잡만 다시 돌린다(캐시 개선은 S17)
- PR: #33

## 2026-11-27 S12 판정 배치와 기수 자동 연장 (세션 1/1)
- 한 일: 만들 것 1~8. V10(`shedlock`, `seasons.settling_started_at` + 상태 CHECK), ShedLock 7.11.0(`common.scheduling`, DB 시각), judgment `DailyJudgmentBatch`(스터디마다 기수 준비 → 판정 시각이 지난 날을 오래된 순서로 → 멤버마다 트랜잭션 → 오류 없을 때만 진행 지점), `JudgmentSchedule`(5분, `@SchedulerLock` 30분/1분), `JudgmentRule`, study `StudySeasons`(스터디 행 잠금, 다음 기수 행 되풀이 생성·진행·72시간 뒤 종료)·`JudgingDays`(날·대상·진행 지점·정산 중), progress `MemberDayJudgments`(행 만들기·잠그기·`result IS NULL`일 때 저장, `BATCH`), 규칙 변경 때 다음 기수 행 끝 날짜 다시 세기, 현황 `penaltyType`·`fineAmount`·`members[].failCount`·`fineTotal`, `ModuleRules` judgment → study, 결정 제안서 008(기록용)
- 상태: 빌드 통과 (테스트 761개). 구현 전 새 테스트 31개 중 30개 실패(통과 1개는 껍데기가 늘 FAIL을 돌려준 `otherwiseFail`). 조건 빼 보기 18개 모두 해당 테스트 실패. spec-reviewer '수정 필요' → 필수 1(5.10·architecture 6장 ⑤도 스펙 변경 제안에)과 고려 4건 반영(테스트 1개 더함, 008 지표 설명, 재기동 직후 구간은 열린 질문)
- 사람이 정한 것 (2026-11-27, 지시 "S12 시작해", 세션 선택지, 모두 Recommended): 브랜치 "feat/S12-daily-judgment", 쿼리 수 "조회만 일정, 멤버별 잠금"(수용 기준 문구는 스펙 변경 제안), 정산 확정 "실제 판정 시각 + 72시간", 정산 표시 "모든 멤버에게 멤버마다". PR 열린 질문 1 (2026-11-27, 지시 "PR의 열린 질문 장단점을 비교해서 제시해 선택할게", 세션 선택지 Recommended): 기수 하나보다 긴 장애 뒤 배치가 기수 행을 채우기 전 구간은 "A. 지금대로 둠"(코드 변경 없음, 5.13 알려진 한계로 적자는 스펙 변경 제안 7)
- 다음: PR 검토(판단 기록 '꼭 봐 주세요': 현황 응답 모양, `result_source` 값, ShedLock 시간) → 병합 → 받아들인 스펙 변경 제안을 docs PR로(쿼리 수 문구 세 곳, data-model `settling_started_at`·`result_source`, R13·5.13·3.4 정산 72시간, api.md 현황 필드, 알려진 충돌 9·11번, 5.13 알려진 한계 한 줄) → S13 또는 S14
- 막힌 것: 없음
- 참고: 판정 테스트는 `DailyJudgmentBatch#judgeStudy`로 스터디 하나씩 돌린다. 모든 스터디를 도는 `run()`·ShedLock 진입점은 공유 DB에 쌓인 2026년 스터디를 피하려고 2025년 날짜로 본다(멤버십 가입 시각도 그해로). 테스트 설정은 `studybot.judgment.cron: "-"`. S13 알림 생성도 `common.scheduling`과 `@SchedulerLock`을 쓰면 된다. S14 결과 알림은 판정이 남긴 `result`·`judged_at`과 `last_judged_day`로 DB에서 다시 맞출 수 있다(judgment는 notification을 부르지 않음). 정산 중은 `last_judged_day`가 끝 날짜에 닿을 때, 종료는 `settling_started_at + 72h` 뒤 첫 배치. Maven Central 429가 잦으니 의존성을 더하면 일찍 받아 두기
- PR: #32

## 2026-11-26 S11 스펙 변경 제안 반영 (docs PR, 단계 진행 아님)
- 한 일: PR #29(병합)의 결정과 받아들인 스펙 변경 제안을 문서에 반영. R7(그 스터디의 고른 날만, 유예 중 전날은 R4 전날 후보일 때만, '승인 필요'는 승인 API 전까지 409, 하루 5건에 철회 포함, 철회 때 같은 날 다른 풀이로 바꿔 끼움·다음 날 유예 안 같은 문제 풀이가 있으면 철회 불가), 5.6(만들 것·주의할 것, 수동 인증 비율은 그날이 속한 기수·방장만), api.md 4.4(승인 API 세 줄 '미정', 수동 인증·면제권 요청·응답, 현황 `exemptionsRemaining`·`selfReportRatio`, 오류 `code` 목록), architecture 알려진 충돌 6번, data-model '수동 인증 테이블' 메모·인덱스, S11 만들 것 2·4
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것: S11 세션(2026-11-26)의 결정 "그 스터디의 고른 날만", "409로 거절", "이번 기수", "R4 전날 조건 적용", "다른 풀이로 바꿔 끼움", "철회도 셈", "모두에게", "그런 push가 있으면 철회를 막음". 2026-11-26 지시 "이제 다음은 뭘 해?" 뒤 세션 선택지: 순서 "스펙 docs PR → S12", 반영 범위 "api.md 요청·응답·오류 code", "data-model self_reports 메모", "'승인 필요' 승인 API 제안", "지난 날 상세의 기수 해석", CodeRabbit 지적 "작은 fix PR로 5곳 모두"
- 다음: 이 PR과 테스트 fix PR 병합 → 새 세션에서 S12(판정 배치와 기수 자동 연장, 선행 S11 완료, OD-07 확정, 결정 제안서 008: 판정 시점과 따라잡기). '한 세션에 단계 하나'라 S12는 이 세션에서 하지 않음
- 막힌 것: 없음
- 참고: 승인 API를 맡을 단계와 승인할 때 그날이 이미 판정됐으면 어떻게 할지는 열린 질문(PR 본문)
- PR: #30. 테스트 fix PR(CodeRabbit PR #29 지적, `awaitQuietly` → `awaitRelease`)은 따로 연다. spec-reviewer '수정 필요' → 필수 1(철회 막는 조건에 '수동 인증 아님' 빠짐)과 고려 반영

## 2026-11-26 S11 수동 인증과 면제권 (세션 1/1)
- 한 일: 만들 것 1~6. V9 `self_reports`. 수동 인증 `POST·DELETE /api/studies/{id}/self-reports`(verification `SelfReportService`, 풀이 기록 SELF_REPORT → progress 포트 `SelfReportCounting`가 그 스터디의 고른 날 멤버 일을 잠그고 센다, 하루 5건은 잠근 뒤 셈, 철회하면 WITHDRAWN·풀이 기록 IGNORED). 면제권 `POST /exemptions`·`DELETE /exemptions/{studyDay}`(progress `ExemptionService`, 오늘만, 멤버십 행 → 멤버 일 행 잠금, 기수 기간에서 SUCCESS가 아닌 사용을 셈). 현황 `members[]`에 `exemptionsRemaining`, 방장에게만 `selfReportRatio`(조회 포트 `SelfReportCounts`). study `StudyAccess#lockMember`, `MemberStudies#rulesOf`(`MemberStudyRules`), `MemberStudy#seasonOn`, `StudyDayContext`에 기수 기간·기수당 면제권
- 상태: 처음 PR을 연 때 빌드 통과 (테스트 716개). 구현 전 새 테스트 61개 중 58개 실패(통과 3개는 엔드포인트가 없어도 404인 T19). 조건 빼 보기 21개 모두 해당 테스트 실패(처음에 1개가 빠져 '마지막 면제권을 쓴 날 다시 써도 200' 테스트를 더함). spec-reviewer '수정 필요' → 필수 1(V9의 상태·결정 시각 CHECK가 승인 흐름을 막음, CHECK 뺌)과 고려 6건 반영(`solve_record_id` NULL 허용, 철회의 판정된 날 409는 `SELF_REPORT_DAY_JUDGED`, 나머지는 PR 본문)
- 사람이 정한 것 (2026-11-26, 지시 "S11 시작해 OD-9부터 결정해야해. 선택지와 장단점을 제시하면 선택할게", 세션 선택지, 모두 Recommended): OD-09 "당일만"(docs PR #28), 브랜치 "docs PR 먼저 + feat/S11", 수동 인증 범위 "그 스터디의 고른 날만", '승인 필요' 모드 "409로 거절", 수동 인증 비율 "이번 기수"
- PR 열린 질문 결정 (2026-11-26, 지시 "열린 질문 선택지와 장단점 알려줘 선택할게", 세션 선택지, 모두 Recommended): 유예 중 전날 수동 인증 "R4 전날 조건 적용"(잠근 전날 행으로 `AssignmentPlan.decide`, 아니면 409 `PREVIOUS_DAY_NOT_OPEN`), 철회 "다른 풀이로 바꿔 끼움"(그 스터디 일에 받은 같은 문제의 CONFIRMED·수동 인증 아님·이 스터디에서 아직 세지 않은 풀이로 집계 행을 바꿈, 조회는 `SolveRecordDetails#countableSolveIds`), 하루 5건 "철회도 셈", 남은 면제권 "모두에게"(뒤 둘은 지금대로). 결정 테스트 48개 중 5개 실패 확인 뒤 구현. 새 조건 빼 보기 9개 모두 걸림. spec-reviewer 2차 '통과'(고려 6건 반영: 바꿔 끼움 조건의 사람·에이전트 구분, 다음 날 유예 push 빈 곳을 열린 질문 5로, 받은 시각 순서·IGNORED 테스트, 409 문구, 이미 센 풀이 조회에 user_id). 빌드 통과 (테스트 725개)
- 열린 질문 5 결정 (2026-11-26, 지시 "열린 질문 5번도 선택지와 장단점", 세션 선택지 Recommended): "그런 push가 있으면 철회를 막음". 같은 날 push로 바꿔 끼울 수 없고 같은 문제 push(수동 인증 아님)를 다음 날 유예 안 `[startOf(D+1), graceEndOf(D))`에 받았으면 철회 409 `SAME_PROBLEM_PUSHED_IN_GRACE`. 테스트 53개 중 2개 실패 확인 뒤 구현, 조건 빼 보기 6개 모두 걸림(1개는 처음에 1ns 변형이라 DB 정밀도에 묻혀 안 걸려 1초로 다시 확인, 그 경계 테스트를 00:00으로). spec-reviewer 3차 '수정 필요' → 필수 1(409 문구에 에이전트가 덧붙인 '한 번만 세요'가 목표 1에서 틀림) 반영, 고려 반영(문구 '하루가 바뀐 뒤', 막지 않는 기록 테스트). 빌드 통과 (테스트 729개)
- 다음: PR 검토 → 병합(#28과 S11 단계 문서가 겹치면 master 병합) → 받아들인 스펙 변경 제안을 docs PR로(R7·5.6 범위, 승인 모드 409와 승인 API 제안, 비율 범위, api.md 요청·응답·`code`·남은 면제권 모두에게, data-model `self_reports` 메모·하루 5건 철회 포함, R7 유예 중 전날 R4 조건, R7·5.6 철회 때 바꿔 끼움·다음 날 유예 push면 철회 막음) → S12(판정 배치) 또는 S13
- 막힌 것: 없음
- 참고: S12는 면제권을 쓴 날 목표를 채우면 SUCCESS로 판정하면 된다. 남은 개수 계산이 `result IS DISTINCT FROM 'SUCCESS'`라 판정 결과만 바꾸면 면제권이 돌아온다. 잠금 순서는 이렇다: 면제권은 멤버십 → 멤버 일, 수동 인증 철회는 수동 인증 → 풀이 기록 → 멤버 일, 제출은 멤버 일 하나만. 판정은 멤버 일만 잠그니 순환이 없다. S13의 알림 대상 제외는 `exemption_used_at`으로 본다. 처음 열린 질문 넷은 모두 정함(위). #28 병합 뒤 master를 이 브랜치에 병합하고 progress·S11 단계 문서 충돌을 풀었다(두 기록 모두 남김, #28이 더한 OD-09 수용 기준 줄 체크). 세션 재시작 뒤 dockerd가 오래된 pid로 안 뜨면 `CLAUDE_CODE_REMOTE=true bash scripts/cloud-session-setup.sh`
- PR: #29

## 2026-11-26 OD-09 결정 반영 (docs PR, 단계 진행 아님)
- 한 일: 사람이 정한 OD-09(면제권 미리 쓰기 = 당일만)를 결정 대장(확정), R8, domain-rules 머리의 '알려진 빈 곳' 줄, pseudocode '면제권 사용'(`day == cal.studyDayOf(now)`), 5.7 확인 단계, S11 단계 문서(결정 게이트, 만들 것 5, 수용 기준 한 줄, 주의할 것, 판단 기록 줄)에 반영
- 상태: 빌드 확인 안 함 (문서만 바꿈, CI가 돎)
- 사람이 정한 것 (2026-11-26, 지시 "S11 시작해 OD-9부터 결정해야해. 선택지와 장단점을 제시하면 선택할게", 세션 선택지, 모두 Recommended): OD-09 "당일만", 브랜치 "docs PR 먼저 + feat/S11". 같은 세션에서 S11 구현 쪽 선택(수동 인증 범위 "그 스터디의 고른 날만", 승인 모드 "409로 거절", 수동 인증 비율 "이번 기수")도 정함. 이 셋은 S11 PR의 스펙 변경 제안으로 올림
- 다음: 이 PR 병합. 같은 세션에서 S11 구현(`feat/S11-self-report-exemption`)을 이어 함
- 막힌 것: 없음
- 참고: 에이전트가 쓴 문장(PR 본문에 표시): S11 '판단 기록이 필요한 곳'의 '면제권의 검증 조건 (OD-09: 당일만)'(원래 줄 '면제권 미리 쓰기 (OD-09 결과에 따라 검증 조건)'을 결정에 맞춤). spec-reviewer '통과', 고려 3건 반영(경계 경합 설명 문장은 빼고 S11 PR에 적음, 오래된 S05 기록 한 줄로)
- PR: #28

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
- 요약: V7 `solve_records`, `PushEventHandler`(삭제·연결 없음·기본 브랜치 아님 → IGNORED), `GithubSolveRecorder`(문제마다 CONFIRMED, 식별 못 한 커밋은 확인 대기 하나, 인정 시각 = 수신함 `received_at`), 배정 연결 지점 `StudyDayAssignment`. 사람이 정함: 빈 `path_prefix` "레포 전체", 확인 대기 "커밋마다 하나", 기본 브랜치 "레포마다", 경로 접두사 설정 "지금은 만들지 않음". 빌드 통과(500개). PR: #18

## 2026-11-24 S07 후속: 설치 삭제 시각 deleted_at (V6, 같은 세션)
- 요약: V6(`github_installations.deleted_at` + V5 시절 DELETED 행 백필 + 상태 CHECK). 사람이 정함: '삭제된 지 30일' 기준 "deleted_at 컬럼", "새 후속 PR로 V6". 교훈: 작업 전·마이그레이션을 고치기 전에 `git fetch origin master`로 병합 여부를 본다(병합된 V5를 고친 커밋을 push했던 일). 빌드 통과(463개). PR: #16, 재발 방지 chore PR #17

## 2026-11-23 S07 GitHub App 연결 (세션 1/1)
- 요약: V5(`github_installations`, `linked_repositories`), 설치 웹훅 처리기, 가입 때 연결(커밋 뒤 `@Async`), App JWT·설치 토큰 캐시, `GET /api/github/install-url`·`GET /github/setup`(302)·레포 목록(1개면 자동 연결)·`PUT /api/me/linked-repository`. 리뷰 반영으로 웹훅 행 잠금, `repositories_revision`, 잠근 뒤 확인은 네이티브 쿼리(open-in-view). 사람이 정함: 키는 파일 경로로만, 계정 값 NULL 허용, 설치 중 + 삭제 뒤 30일. 빌드 통과(460개). PR: #14, docs PR #15

## 2026-11-23 S06 후속 결정 반영 (docs PR, 단계 진행 아님)
- 요약: PR #12에서 사람이 정한 것(네 이벤트 모두 처리기 필수, ping 200, `last_error`는 예외 종류, payload 14일 뒤 비움, 5MB 초과 413 + 경고 로그)을 S08·pseudocode·data-model·5.3에 반영. PR #12 '그 밖에 발견한 것'(OSIV, 수신함 엔티티 위치, 헤더 제어 문자 → 500)은 정하지 않음. PR: #13

## 2026-11-23 S06 웹훅 수신 기반 (세션 1/1)
- 요약: V4 `webhook_inbox`, `POST /webhooks/github`(본문 5MB까지, 서명, 수신함 INSERT ON CONFLICT 별도 트랜잭션 → 새 트랜잭션에서 처리기 + 상태, 실패면 FAILED), 시크릿 교체(현재·옛), 처리기 인터페이스 `github.webhook.GithubEventHandler`, `PingEventHandler`. 사람이 정함: ping "수신함에 넣고 200", 본문 한도 "5MB 유지 + 경고 로그", `last_error` "예외 종류만". `spring.jpa.open-in-view`가 켜져 있음. 빌드 통과(376개). PR: #12

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
