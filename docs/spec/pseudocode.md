# 의사코드

> 스펙 문서예요. 사람이 정한 내용만 담아요. 에이전트는 사람이 정한 결정을 `docs/` 브랜치의 문서 전용 PR로 반영하고, 그 밖에 바꿀 게 있으면 PR의 '스펙 변경 제안'에 적어요 (`docs/workflow.md` 4장).
>
> - 설계 의도를 보여 주는 의사코드예요. 그대로 옮기지 말고, 부딪히면 규칙(`domain-rules.md`)과 모듈 의존 규칙(`architecture.md` 4.2)을 우선해요.
> - 알려진 충돌: '판정 배치'의 `createNotification(DAILY_RESULT, ...)`은 judgment → notification 의존이라 4.2 그래프에 없어요. 의존 규칙이 우선이에요 (`architecture.md` '알려진 충돌과 빈 곳' 2번).

## 스터디 일 배정과 풀이 기록 (R4, R5)

```
assignStudyDay(study, user, t):
    cal = StudyCalendar(study)
    N = cal.studyDayOf(t)
    if t >= cal.boundaryOf(N) + rule(N).grace:     -- 유예 밖
        return N
    prev = N - 1
    md = lockMemberDay(study, user, prev)          -- INSERT ON CONFLICT DO NOTHING + SELECT FOR UPDATE
    if md.result is null
       and cal.isJudgeTarget(user, prev)           -- 가입·탈퇴·기수·쉬는 날
       and md.exemptionUsedAt is null
       and md.solvedCount < rule(prev).dailyGoal:
        return prev
    return N

recordSolve(user, problem, receivedAt, source):
    solve = insert solve_records(...) on conflict do nothing
    if solve was duplicate: return
    for study in activeStudiesOf(user):            -- 한 트랜잭션, 잠금 순서: 전날 → 오늘
        day = assignStudyDay(study, user, receivedAt)
        lockMemberDay(study, user, day)
        if insert study_day_solves(study, user, day, problem) on conflict do nothing:
            member_days.solvedCount = count(study_day_solves where study, user, day)
```

## push 웹훅 처리 (5.3)

```
onGithubWebhook(rawBody, headers):
    if not verifyGithub(rawBody, headers["X-Hub-Signature-256"]): return 401
    event = headers["X-GitHub-Event"]
    if event not in {push, installation, installation_repositories, ping}: return 204
    inbox = insert webhook_inbox(GITHUB, headers["X-GitHub-Delivery"], rawBody, now)
            on conflict do nothing                   -- 별도 트랜잭션으로 먼저 커밋
    if inbox was duplicate: return 200
    try:
        process(inbox)                               -- P1: 같은 요청 안, 새 트랜잭션
        inbox.status = PROCESSED (또는 IGNORED)
    catch e:
        inbox.status = FAILED, inbox.lastError = 예외 종류(e)  -- 메시지는 로그에만 (개인정보가 들어갈 수 있음)
    if event == ping: return 200                     -- 5.3 응답 코드 표. ping도 수신함에는 남김
    return 202

processPush(inbox):
    p = parse(inbox.payload)
    if p.deleted: return IGNORED
    repo = activeLinkedRepo(p.repository.id) ?: return IGNORED
    if p.ref != "refs/heads/" + repo.defaultBranch: return IGNORED   -- 연결 레포를 찾은 뒤 그 레포의 기본 브랜치와 비교
    for c in p.commits:
        parsed = parser.parse(c.message, c.added + c.modified)
        if parsed.problems is empty:                  -- 확인 대기는 커밋마다 하나 (5.4)
            path = first(parsed.unidentifiedPaths where inPrefix(path, repo.pathPrefix))   -- 접두사가 비면 레포 전체
            if path: insert solve_records(repo.user, PENDING_IDENTIFICATION, c.sha, raw_path = path,
                                          received_at = inbox.receivedAt) on conflict do nothing
            continue
        for problem in parsed.problems:
            recordSolve(repo.user, problem, inbox.receivedAt, GITHUB)   -- 인정 시각 = 수신 시각
```

## 판정 배치 (R9, R10)

```
every 5 minutes with ShedLock:
  for study in activeStudies:
    startSeasons(study)       -- 시작일이 된 기수는 ACTIVE, 마지막 기수 행이 시작했으면 다음 기수 행(SCHEDULED)을 만듦 (5.13)
                              -- 길이는 그 시작일의 규칙, 끝 날짜는 StudyCalendar.seasonEndDay. 스터디 행 잠금, 다시 돌려도 안전
    day = study.lastJudgedDay + 1
    while cal.judgeAt(day) <= now:                   -- 기준 시각 + 유예 + 10분
        for member in judgeTargets(study, day):
            transaction:
                md = lockMemberDay(study, member, day)
                if md.result is not null: continue  -- 다시 돌려도 안전
                rule = study.ruleAt(day)
                if isRestDay(rule, day):              md.result = EXEMPT_REST
                elif md.solvedCount >= rule.dailyGoal: md.result = SUCCESS   -- 면제권은 반환 처리
                elif md.exemptionUsedAt is not null:  md.result = EXEMPT_TICKET
                else:
                    md.result = FAIL
                    if rule.penaltyType != NONE:      -- P2부터
                        insert ledger(CHARGE, rule.fineAmount, key = "JUDGE:" + md.id)
                md.judgedAt = now
        study.lastJudgedDay = day                     -- 하루치가 모두 끝난 뒤
        createNotification(DAILY_RESULT, study, day)
        day = day + 1
```

## 마감 전 알림 (5.8)

```
every minute with ShedLock:                          -- 생성
  for study in activeStudies:
    day = cal.studyDayOf(now)
    if study.lastRemindedDay == day: continue
    if now < cal.reminderAt(day): continue
    if now >= cal.boundaryOf(day + 1): continue      -- 마감이 지났으면 만들지 않음
    transaction:
        for m in judgeTargets(study, day)
                 where not rest day and exemption not used and solvedCount < goal:
            insert notifications(REMINDER, m, day, expiresAt = boundaryOf(day + 1)) on conflict do nothing
        if any targets:
            insert notifications(SHARE_PROMPT, owner, day) on conflict do nothing
            if study.discordEnabled: insert notifications(DISCORD_STATUS, study, day) on conflict do nothing
        study.lastRemindedDay = day

every 5 seconds:                                      -- 발송
    rows = select from notifications
           where status = PENDING and nextAttemptAt <= now
           order by id limit 100 for update skip locked
    for n in rows:
        n.status = SENDING, n.lockedUntil = now + 2m  -- 먼저 커밋
        if now >= n.expiresAt:            n.status = EXPIRED; continue
        if n is REMINDER and (solved or exempted): n.status = SKIPPED; continue
        r = send(n)
        201       → SENT
        404, 410  → delete subscription, FAILED
        429, 5xx  → PENDING, nextAttemptAt = backoff(n.attempts++)
    -- 별도 정리: SENDING인데 lockedUntil이 지난 행은 PENDING으로 되돌림
```

(`studies.last_reminded_day`는 판정 배치의 `last_judged_day`와 같은 역할의 진행 지점이에요.)

## 면제권 사용 (R8)

```
useExemption(study, user, day):
    require now < cal.boundaryOf(day + 1) and not restDay(day)
    transaction:
        lock memberships(study, user)                  -- 이 사용자의 면제권 계산을 한 줄로 세움
        md = lockMemberDay(study, user, day)
        require md.exemptionUsedAt is null and md.solvedCount < goal
        used = count member_days in current season
               where exemption_used_at is not null and result is distinct from SUCCESS
        require used < rule.exemptionsPerSeason
        md.exemptionUsedAt = now
```

## 이의 결정 (R11, P2)

```
decideAppeal(appeal, accept):
    transaction:
        md = member_days by appeal (version 포함)
        if accept and appeal.type == JUDGMENT and md.result == FAIL:
            md.result = SUCCESS, md.resultSource = APPEAL
            insert ledger(REVERSAL, chargedAmount(md), key = "APPEAL:" + appeal.id)
        if accept and appeal.type == SELF_REPORT_OBJECTION:
            report.status = REJECTED, md.solvedCount 다시 계산
            if md.result == SUCCESS and md.solvedCount < goal:
                md.result = FAIL
                insert ledger(CHARGE, fine, key = "APPEAL:" + appeal.id)
        appeal.status = accept ? ACCEPTED : REJECTED
    -- version 충돌이면 예외 → 409 → 다시 시도
```

## 서명 검증 (Java)

```java
// GitHub: X-Hub-Signature-256 = "sha256=" + HMAC-SHA256(secret, 원본 바이트)
boolean verifyGithub(byte[] body, String header, byte[] secret) throws GeneralSecurityException {
    if (header == null || !header.startsWith("sha256=")) return false;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret, "HmacSHA256"));
    byte[] expected = mac.doFinal(body);
    byte[] actual;
    try {
        actual = HexFormat.of().parseHex(header.substring("sha256=".length()));
    } catch (IllegalArgumentException e) {
        return false;                                  // 16진수가 아니면 거절
    }
    return MessageDigest.isEqual(expected, actual);    // 상수 시간 비교
}

// 디스코드: 공개키(hex 32바이트)를 X.509 형식으로 감싸야 JDK가 읽을 수 있음
static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

PublicKey discordPublicKey(String hex) throws GeneralSecurityException {
    byte[] raw = HexFormat.of().parseHex(hex);
    byte[] encoded = new byte[ED25519_X509_PREFIX.length + raw.length];
    System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
    System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
    return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
}

boolean verifyDiscord(PublicKey key, String signatureHex, String timestamp, byte[] body)
        throws GeneralSecurityException {
    Signature sig = Signature.getInstance("Ed25519");
    sig.initVerify(key);
    sig.update(timestamp.getBytes(StandardCharsets.UTF_8));  // 타임스탬프 + 원본 바이트 순서
    sig.update(body);
    return sig.verify(HexFormat.of().parseHex(signatureHex));
}
```
