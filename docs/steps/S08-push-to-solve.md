# S08. push 처리와 풀이 기록

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S03, S06, S07 |
| 결정 게이트 | 없음 |
| 사람 할 일 | 없음 |
| T번호 | T3 |

## 목표

push 웹훅의 커밋에서 문제를 식별해 풀이 기록을 남긴다. 풀이를 스터디 일에 배정하는 건 S09다.

## 읽을 문서

- `docs/spec/processes/5.03-push-webhook.md`: 'push 처리 순서', '주의할 것'
- `docs/spec/processes/5.04-problem-identification.md`: 흐름도의 '확인 대기'
- `docs/spec/pseudocode.md`: `processPush`, `recordSolve`의 앞부분
- `docs/spec/data-model.md`: `solve_records`
- `docs/spec/domain-rules.md`: R3, R6

## 만들 것

1. **push 처리기 (S06의 처리기 인터페이스):**
    - `deleted == true`이거나 `ref`가 연결 레포의 기본 브랜치가 아니면 `IGNORED`
    - `repository.id`로 활성 연결 레포를 찾고, 없으면 `IGNORED`
    - 커밋마다 추가·수정된 파일 경로와 메시지를 파서(S03)에 넣는다
2. **풀이 기록:** 문제마다 `solve_records`를 INSERT하고, `(user_id, commit_sha, platform, problem_no)`가 이미 있으면 무시한다. 출처 `GITHUB`, 상태 `CONFIRMED`.
3. **확인 대기:** 식별하지 못했지만 연결할 때 지정한 경로 접두사 안의 파일이면 `PENDING_IDENTIFICATION`으로 남긴다.
4. **인정 시각:** `webhook_inbox.received_at`. 처리한 시각이 아니다 (R3, D5).
5. **파서 버전과 원본 경로:** 풀이 기록에 남긴다 (5.4).
6. **배정 연결 지점:** 풀이 기록이 생긴 뒤 스터디 일 배정을 부를 자리를 만든다. 배정 자체는 S09에서 채운다.

## 만들지 않을 것

- 스터디 일 배정, `study_day_solves`, `member_days` (S09)
- `commits`가 비었는데 `before`와 `after`가 다를 때 compare API로 보완하기 (P2). 지금은 경고 로그만 남긴다

## 수용 기준

- [ ] T3: 문제 2개를 각각 커밋한 push → 풀이 기록 2개
- [ ] 같은 push를 다시 받아도(다른 delivery id, 같은 커밋) 풀이 기록이 늘지 않는다
- [ ] 기본 브랜치가 아닌 push, 브랜치 삭제 push → `IGNORED`, 기록 없음
- [ ] 연결하지 않은 레포의 push → `IGNORED`
- [ ] 식별 실패 + 경로 접두사 안의 파일 → `PENDING_IDENTIFICATION` 기록 하나
- [ ] README만 고친 커밋 → 기록 없음
- [ ] 풀이 기록의 인정 시각은 수신함의 수신 시각과 같다 (처리 시각을 일부러 다르게 만든 테스트)

## 주의할 것

- payload에는 커밋 작성자 이메일이 있다. 풀이 기록에는 필요한 필드만 저장한다 (R16, 9.8).
- force push로 같은 문제가 새 SHA로 와도 같은 날 중복은 S09의 유니크 제약이 막는다. 여기서는 기록이 하나 더 생기는 게 맞다.

## 판단 기록이 필요한 곳

- 확인 대기를 어떤 단위로 남기는지 (파일마다 / 커밋마다)
- 배정 연결 지점의 모양 (같은 트랜잭션에서 부를지)
