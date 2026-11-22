# S17. 배포 산출물

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S01 (실제 적용은 P1 막바지) |
| 결정 게이트 | 없음 |
| 사람 할 일 | H-07 (인프라 계정·서버 적용), H-04 (VAPID 키), H-08 (개인정보처리방침) |
| T번호 | 없음 (빌드·설정 검증) |

## 목표

레포 안에 들어가는 배포 산출물을 만든다. 사람이 서버에 적용만 하면 9장의 구성(풀 방식 배포, Tunnel, 백업)이 돌아가게 한다. 에이전트는 실제 서버, 계정, 비밀값에 접근하지 않는다.

## 읽을 문서

- `docs/spec/infra.md` 전체 (특히 9.1, 9.2, 9.5, 9.6, 9.10)
- `docs/spec/architecture.md`: 4.1 구성도
- `docs/spec/security.md`: 9.7 '운영 엔드포인트와 오류'의 보안 헤더 항목
- `docs/decisions/README.md`: D10~D16
- `docs/decisions/004-db-schema-name.md`: '적용 방법'의 역할과 권한, 백업과 복구

## 만들 것

1. **Dockerfile:** root가 아닌 사용자로 실행, JVM이 컨테이너 메모리를 인식, 베이스 이미지는 다이제스트로 고정.
2. **Docker Compose:** cloudflared + Nginx + Spring. 내부 Docker 네트워크로만 연결하고 호스트 포트를 공개하지 않는다. `docker.sock`을 어떤 컨테이너에도 마운트하지 않는다. 비밀값은 `.env`에서 읽고, `.env`는 레포에 없다 (`.env.example`만).
3. **Nginx 설정:** `/`는 정적 파일, `/api/*`·`/oauth2/*`·`/login/*`·`/webhooks/*`는 Spring으로 (4.1). 보안 헤더(CSP에 카카오 SDK 도메인만 추가, `frame-ancestors 'none'`, `X-Content-Type-Options`, `Referrer-Policy`), `client_max_body_size`, `sw.js`·`index.html`은 캐시하지 않음.
4. **CI 확장:** 테스트 → 이미지 빌드 → Trivy 검사 → GHCR에 sha 태그 push → 통과하면 `prod` 태그 이동. 이미지 push 잡만 `packages: write`. 서드파티 액션은 커밋 SHA로 고정.
5. **서버 배포 타이머 스크립트:** 1분마다 `prod` 다이제스트 확인 → 바뀌면 새 컨테이너 기동 → `/actuator/health` 확인 → 실패하면 직전 다이제스트로 복귀 → 운영 채널에 알림 (9.2). systemd 유닛 파일 포함.
6. **백업 스크립트:** 매일 04시 `pg_dump`(백업 전용 계정, 대상 스키마 `studybot`) → age 암호화(서버에는 공개키만) → R2 업로드, 실패하면 운영 채널 알림 (9.6, 결정 제안서 004). systemd 타이머 포함.
7. **런북:** `docs/ops/runbooks/`에 secret-rotation, restore(`studybot` 스키마 기준), webhook-replay, rejudge.
8. **Dependabot:** Gradle, Docker, GitHub Actions.
9. **결정 제안서 (기록용):** 배포 구성. 계획서 ADR-001 주제로, 9.2의 선택지 비교를 근거와 함께 남긴다.

## 만들지 않을 것

- 실제 서버 생성, 계정 설정, 비밀값 입력, Cloudflare·Supabase 설정 (사람, H-07)
- 블루-그린 무중단 배포 (P4)

## 수용 기준

- [ ] `docker build`가 성공하고, 컨테이너가 root가 아닌 사용자로 뜬다
- [ ] `docker compose config`가 통과하고, 어떤 서비스도 호스트 포트를 공개하지 않는다
- [ ] 셸 스크립트가 `shellcheck`를 통과한다
- [ ] 워크플로의 권한이 잡마다 최소이고, 서드파티 액션이 SHA로 고정돼 있다
- [ ] 레포와 이미지에 비밀값이 없다 (gitleaks 같은 도구로 검사, 결과를 PR에)
- [ ] 런북마다 '언제 쓰나 / 순서 / 확인 방법'이 있다

## 주의할 것

- 9.2의 '제외한 방식'(Actions에서 SSH, 셀프 호스티드 러너, Watchtower)을 쓰지 않는다.
- Cloudflare나 Nginx가 웹훅 본문을 바꾸면 서명이 깨진다. 웹훅 경로에는 어떤 변환도 걸지 않는다 (5.3).
- CI 워크플로를 바꾸니 PR에서 '꼭 봐 주세요'로 표시한다.

## 판단 기록이 필요한 곳

- **결정 제안서 (기록용):** 배포 구성 (위 9번)
- 베이스 이미지 선택, JVM 메모리 설정 값
