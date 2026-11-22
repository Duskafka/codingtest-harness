# 외부 시스템과 제약

> 스펙 문서예요. 사람이 정한 내용만 담아요. 에이전트는 사람이 정한 결정을 `docs/` 브랜치의 문서 전용 PR로 반영하고, 그 밖에 바꿀 게 있으면 PR의 '스펙 변경 제안'에 적어요 (`docs/workflow.md` 4장).
>
> 아래 제약은 2026-11 기준이에요. 구현하기 전에 링크한 공식 문서로 다시 확인하고, 다르면 PR의 '스펙 변경 제안'에 적어요. 링크는 `docs/spec/references.md`에 있어요.

## 한눈에 보기

| 시스템 | 제약 | 영향을 받는 곳 | 출처 |
|---|---|---|---|
| GitHub 웹훅 | 10초 안에 2XX로 응답해야 해요 | 5.3 | [S33] |
| GitHub 웹훅 | 실패한 전달을 자동으로 다시 보내지 않아요. 전달 목록 조회·재전송 API로 직접 복구해요 | 5.14 | [S34] |
| GitHub 웹훅 | 재전송은 최근 3일 안의 전달만 돼요 | 5.14 | [S35] |
| GitHub 웹훅 | `X-Hub-Signature-256` = HMAC-SHA256(시크릿, 원본 바이트). 상수 시간 비교 | 5.3, 9.7 | [S36] |
| GitHub App | Setup URL의 `installation_id`는 위조될 수 있어요. 서명된 웹훅의 설치자 정보로만 연결해요 | 5.2, T21 | [S37] |
| GitHub API | 설치 토큰 요청 한도는 시간당 5,000건 | 5.14, 6장 ⑥ | [S38] |
| GitHub push 이벤트 | `commits` 목록은 잘릴 수 있어요 (최대 2,048개) | 5.3 | [S39] |
| GitHub API | 레포 활동 목록에서 push와 그 시각을 볼 수 있어요 | 5.14 | [S40] |
| 백준허브 | 커밋 메시지와 경로 형식이 정해져 있지만, 재개된 백준에서 바뀔 수 있어요 | 5.4 | [S41] |
| 디스코드 상호작용 | 3초 안에 첫 응답, 후속 메시지 토큰은 15분 유효 | 5.15 | [S31] |
| 디스코드 상호작용 | Ed25519 서명, 헤더 `X-Signature-Ed25519`, `X-Signature-Timestamp` | 5.15 | [S32] |
| 카카오톡 공유 | 사용자가 직접 대상을 고르는 방식이라 자동화할 수 없고, 대신 검수가 필요 없어요. 메시지 API 자동 발송은 지양해요 | 2.7, 5.9 | [S27][S28] |
| 카카오톡 공유 웹훅 | 3초 안에 2XX | 5.9 (P3) | [S29] |
| 웹 푸시 (iPhone) | iOS 16.4 이상, 홈 화면에 추가한 PWA만 받을 수 있어요 | 2.7, 5.8 | [S30] |
| Supabase | Session pooler(5432)를 써요. Transaction mode(6543)는 prepared statement를 지원하지 않아요 | 9.4 | [S43] |
| Supabase | 무료 플랜은 1주 비활성 시 일시정지, 초과 사용은 과금 대신 제한 | 9.4 | [S42][S44] |

## 개발 중에 외부 시스템 다루기

- 자동 테스트는 외부 시스템에 실제로 접속하지 않아요. GitHub API, 웹 푸시 서비스, 디스코드는 WireMock으로 흉내 내고, 웹훅은 저장해 둔 실제 payload 샘플을 MockMvc로 보내요 (8.1).
- 실제 계정과 키가 필요한 연동 확인은 사람이 해요. 필요한 준비는 `docs/steps/README.md`의 '사람 할 일'에 있어요.
- 개발용 GitHub App과 운영용 GitHub App은 따로예요. 에이전트는 어느 쪽의 키도 받지 않아요. 테스트에는 테스트 안에서 만든 키를 써요.

## 4.6 GitHub App 설정 체크리스트

- 권한: Repository → Contents 읽기 전용, Metadata 읽기 전용 (최소 권한)
- 이벤트: `push` 구독. 설치 관련 이벤트 (`installation`, `installation_repositories`)도 받는지 확인
- Webhook URL: `https://(도메인)/webhooks/github`
- Webhook secret: 충분히 긴 무작위 문자열, 서버 `.env`에만 보관
- Setup URL: `https://(도메인)/github/setup` + "Redirect on update" 켜기 (레포를 추가·삭제했을 때도 돌아오게)
- 설치 가능 범위: Any account
- Private key: 한 번 내려받아 서버에만 보관. 레포에 절대 커밋하지 않기
- 개발용 App과 운영용 App을 따로 등록. 개발용 웹훅은 Cloudflare Tunnel의 개발용 호스트명 (또는 smee.io)으로 로컬에 전달
- 로그인을 GitHub App의 사용자 인증으로 할지, OAuth App을 따로 둘지 결정 (ADR-002)
