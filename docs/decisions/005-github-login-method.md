# 005. GitHub 로그인 방식

| 항목 | 내용 |
|---|---|
| 상태 | 제안 |
| 관련 | OD-02, S04, `docs/spec/api.md` 4.5, `docs/spec/processes/5.02-github-connection.md` '주의할 것', `docs/spec/external-systems.md` 4.6 마지막 항목, `docs/spec/infra.md` 9.6 비밀 표('GitHub OAuth client secret' 행), `docs/spec/data-model.md` 4.3(`users`), `docs/spec/product.md` 2.5(랜딩의 '받는 정보' 표시), H-02·H-03, 계획서 ADR-002의 나머지 |
| 제안 | 에이전트, PR #8 |
| 날짜 | 제안 2026-11-22 |

<!-- 여기부터 '검증 방법'까지 에이전트가 써요 -->

## 배경

S04(로그인과 세션)는 OD-02에 막혀 있어요. 단계 문서의 '만들 것' 1이 이 제안서이고, 사람이 정하기 전에는 로그인을 구현하지 않아요. 로그인 유지 방식(세션·만료·쿠키)은 결정 제안서 002로 확정됐어요. 이 제안서는 **GitHub 신원 확인을 어느 앱 등록으로 받나**만 다뤄요.

- 별도 OAuth App을 등록해 로그인에 쓴다
- 웹훅 때문에 어차피 등록하는 GitHub App의 사용자 인증(user access token)으로 로그인한다

S04 단계 문서가 비교하라는 기준은 넷이에요. 받는 권한 범위, 설치와 로그인의 연결(5.2), 토큰 저장 여부, 운영 부담이에요.

**스펙의 제약**

- Spring Security OAuth2 Client로 로그인해요. GitHub access token은 신원 확인에만 쓰고 저장하지 않아요 (api.md 4.5, S04 수용 기준 "DB와 로그 어디에도 남지 않는다").
- 로그인에서 받는 건 `GET /user`의 id·login·avatar뿐이에요 (4.5 그림). `users`에서 GitHub에서 받아 저장하는 컬럼도 `github_id`, `github_login`, `avatar_url`이에요 (data-model 4.3). 이메일은 받지 않아요 (security.md 9.8 '최소 수집').
- 랜딩에 "받는 정보: GitHub ID·이름·프로필 사진뿐"을 보여 줘서 GitHub 로그인 거부감을 줄이기로 했어요 (product.md 2.5 '전환 깔때기' 표와 화면 목록 1번).
- 설치는 서명된 `installation` 웹훅의 `sender.id`와 `github_id`가 같은 사용자에게만 연결해요. Setup URL의 `installation_id`는 믿지 않아요 (5.2, T21). 5.2 '주의할 것'은 "설치자 확인용 사용자 토큰을 따로 받는 방법도 있어요 (ADR-002)"라고 이 결정을 가리켜요.
- GitHub App 권한은 Repository → Contents 읽기, Metadata 읽기예요 (4.6). 개발용 앱과 운영용 앱을 따로 등록해요 (external-systems '개발 중에 외부 시스템 다루기', 4.6).
- 같은 출처 구성이고 Nginx가 `/oauth2/*`·`/login/*`을 Spring으로 넘겨요 (architecture 4.1). 세션은 DB 세션, 쿠키는 SameSite=Lax + `__Host-Http-`예요 (002).
- 비밀값은 서버 `.env`나 파일에만 두고, 에이전트는 어느 키도 받지 않아요 (infra 9.6, external-systems).

**앞선 결정에서 이어 온 조건**

- Android·iOS 앱을 P1 이후에 만들 계획이 있어요. OD-01 때 사람이 답한 조건이에요 (결정 제안서 002 배경). 이 결정도 앱을 막지 않는 쪽이 좋다고 보고 이어 적용했어요. 다만 스펙은 아직 바뀌지 않았어요. product.md 2.4 '하지 않는 것'에는 "모바일 네이티브 앱 (PWA로 충분)"이 그대로 있어요 (002 PR의 스펙 변경 제안).

**사람이 이번 세션에서 답한 조건 (2026-11-22)**

| 질문 | 답 |
|---|---|
| 추천 기준 | 보안과 운영 단순성 (OD-01과 같음) |

## 선택지

세 선택지 모두 로그인 흐름은 같아요. `https://github.com/login/oauth/authorize`로 보내고, 콜백에서 code를 `https://github.com/login/oauth/access_token`에서 토큰으로 바꾼 뒤, `GET https://api.github.com/user`로 신원을 확인해요. GitHub 문서도 OAuth App의 웹 흐름이 GitHub App user access token을 만드는 흐름과 같다고 해요. 다른 건 **어느 앱 등록의 client ID·secret을 쓰고, 그래서 받은 토큰이 무엇에 닿느냐**예요.

| 선택지 | 장점 | 단점 | 되돌리는 비용 |
|---|---|---|---|
| **A. 별도 OAuth App (scope 없음)**. 환경마다 OAuth App을 하나씩 더 등록 | scope 없이 받은 토큰은 공개 정보 읽기만 할 수 있어서, 새어 나가도 닿는 데가 가장 적어요. 로그인과 GitHub App 설치가 완전히 따로라 한쪽 설정 변경이 다른 쪽에 번지지 않아요 | 토큰이 만료되지 않아요 (사용자가 취소하거나 1년 동안 안 쓸 때까지 유효). 앱 등록이 환경마다 둘이라 모두 넷이에요. 사용자의 GitHub 설정 화면에 같은 서비스가 'Authorized OAuth Apps'와 'Installed GitHub Apps'에 따로 보여요. Spring의 GitHub 기본 scope(`read:user`)를 비워야 'scope 없음'이 돼요 | 낮음: client ID·secret 설정만 바뀌어요. `github_id`는 같은 GitHub 사용자 ID라 `users`가 바뀌지 않아요 |
| **B. GitHub App 사용자 인증**. 웹훅용 GitHub App에 콜백 URL과 client secret을 더해 로그인에도 씀. '설치 중 사용자 인증 요청'은 끄고, '사용자 토큰 만료'는 켜 둠(기본값) | 환경마다 앱 등록이 하나예요. 토큰이 기본 8시간 뒤 만료돼요. GitHub이 일반적으로 GitHub App을 권해요. 설치를 사용자 토큰으로 확인하는 GitHub 권장 방법(`GET /user/installations`)을 나중에 쓸 길이 열려 있어요 | 토큰이 '앱 권한 ∩ 사용자 권한 ∩ 앱이 설치된 계정'에 닿아서, 설치한 뒤에는 고른 레포의 내용 읽기까지 닿아요 (A보다 넓음). 동의 화면에 '대신 행동(Act on your behalf)' 문구가 떠요. 로그인과 설치가 한 앱에 묶여, 앱이 정지되거나 권한을 바꾸면 로그인 화면에도 영향이 가요 | 낮음: A와 같아요 |
| C. B + '설치 중 사용자 인증 요청' 켬 | 설치와 로그인(사용자 토큰)을 한 번의 흐름으로 받아요. 설치 직후 사용자 토큰으로 설치를 확인할 수 있어요 | 이 옵션을 켜면 Setup URL을 쓸 수 없고, 설치 뒤 첫 번째 콜백 URL로 돌아와요. 5.2의 흐름(`GET /github/setup` 화면)이 바뀌어요. GitHub이 시작한 인가라 Spring이 저장해 둔 인가 요청(state)과 맞지 않아 기본 로그인 처리로 받지 못할 것으로 보여요 (추측). 5.2는 이미 서명된 웹훅으로 연결하므로 얻는 게 작아요 | 중간: 5.2 흐름과 Setup URL 처리가 바뀌어요 |

**기준별 비교 (A, B)**

| 기준 | A. 별도 OAuth App | B. GitHub App 사용자 인증 |
|---|---|---|
| 받는 권한 범위 | scope 없음: 공개 정보 읽기만 ("Grants read-only access to public information") | 앱 권한과 사용자 권한이 겹치는 것만, 앱이 설치된 계정 안에서만. 이 서비스는 Contents·Metadata 읽기라, 설치한 뒤에는 고른 레포의 내용을 읽을 수 있어요. 설치 전에는 공개 프로필 확인 정도로 보여요 (해석) |
| 동의 화면 | scope 없음일 때의 문구는 확인하지 못했어요 (추측) | '신원 확인', '접근할 수 있는 리소스 파악', '대신 행동'. 권한이 읽기뿐이라 실제로 쓸 수 있는 동작은 없지만 문구는 그대로 떠요 (해석) |
| 토큰 만료 | 만료 없음. 1년 동안 안 쓰면 GitHub이 취소. 사용자·앱·scope 조합마다 10개까지이고 넘으면 오래된 것부터 취소 | 기본 8시간, 리프레시 토큰 6개월. GitHub이 만료를 켜 두라고 강하게 권해요 |
| 토큰 저장 | 저장하지 않음 (스펙). 아래 '양쪽 공통' | 저장하지 않음 (스펙). 리프레시 토큰도 함께 버림 |
| 설치와 로그인의 연결 (5.2) | 영향 없음. 웹훅의 `sender.id`로 연결 | 영향 없음. 웹훅의 `sender.id`로 연결. 필요하면 사용자 토큰으로 `GET /user/installations`를 확인하는 방법을 더할 수 있어요 |
| 앱 등록 (개발·운영) | GitHub App 2 + OAuth App 2 = 4 | GitHub App 2 |
| 비밀값 | GitHub App private key, 웹훅 시크릿, OAuth client secret | GitHub App private key, 웹훅 시크릿, GitHub App client secret. 개수는 A와 같아요 |
| 사용자 설정 화면 | 같은 서비스가 두 곳에 따로 보이고 따로 취소해요 | 앱 하나. 인증 취소와 설치 제거는 여전히 따로예요 ("You can install a GitHub App without authorizing the app") |
| 조직 정책 | OAuth App 접근 제한을 켠 조직의 리소스에는 조직 관리자 승인이 필요해요. 로그인에는 영향이 없을 것으로 보여요 (추측) | 조직의 애플리케이션 정책을 받지 않아요 |
| 콜백 URL | 10개까지 | 10개까지 |
| 웹훅 | 영향 없음 | 사용자가 인증을 취소하면 `github_app_authorization` 이벤트가 와요 (구독을 끌 수 없음). S06은 모르는 이벤트를 204로 넘기므로 바꿀 게 없어요 |
| Spring 구현 | `CommonOAuth2Provider.GITHUB`. scope를 비우는 설정이 필요해요 | `CommonOAuth2Provider.GITHUB`. scope는 GitHub App에서 무시돼요 ("No other scopes are supported for GitHub Apps") |
| 앱 (P1 이후) | 웹 흐름의 토큰 교환에 client secret이 필요해요 (두 앱 모두 "Required"). 그래서 앱 혼자서는 로그인을 끝낼 수 없고 서버를 거쳐 교환해야 할 것으로 보여요 (추측). 앱 로그인 방식은 002대로 앱을 시작할 때 별도 제안서로 정해요 | A와 같음 |

## 추천과 이유

**B (GitHub App 사용자 인증). '설치 중 사용자 인증 요청'은 끄고, '사용자 토큰 만료'는 켠 채로 둔다.**

사람이 답한 기준(보안과 운영 단순성)으로 보면 이래요.

- **운영 단순성:** 웹훅 때문에 GitHub App은 어차피 환경마다 등록해요. B는 그 앱에 콜백 URL과 client secret만 더하면 돼서, 등록할 앱이 넷에서 둘로 줄어요. 비밀값 개수는 같지만 모두 한 앱 설정 화면에서 관리하고 교체해요. 사용자도 GitHub 설정에서 이 서비스를 한 곳에서 봐요. 사람 할 일 H-03(로그인용 OAuth 설정)이 'OAuth App을 따로 등록하는 일'에서 '개발용 GitHub App에 콜백 URL과 client secret을 더하는 일'로 줄어요 (H-02와 합칠지는 사람이 정해요. 아래 표).
- **보안:** 두 선택지 모두 토큰을 저장하지 않고 신원 확인 직후 버려요. 그러면 토큰이 새는 길은 '처리 중 메모리'와 '실수로 남긴 로그' 정도예요. 그 상황을 가정하면 이래요.
    - **A:** 닿는 범위는 좁아요 (공개 정보). 대신 만료가 없어서 사용자가 취소할 때까지 유효해요.
    - **B:** 닿는 범위는 넓어요 (설치한 레포 읽기). 대신 8시간 뒤 스스로 만료돼요.

  닿는 범위는 A가 낫고, 노출 시간은 B가 나아요. B의 넓은 범위는 이 서비스가 원래 읽는 풀이 레포 읽기 권한이고, 쓰기 권한은 없어요. 그래서 운영 쪽 이점이 분명한 B를 추천해요. 아래 '양쪽 공통'의 즉시 취소를 S04에서 함께 하면 두 선택지의 노출 시간 차이는 더 줄어요.
- **GitHub의 권장:** GitHub은 "In general, GitHub Apps are preferred over OAuth apps."라고 해요. 예외로 든 건 엔터프라이즈 수준 권한뿐이고, 이 서비스와는 관계없어요. GitHub은 GitHub App으로 'Login with GitHub' 버튼을 만드는 튜토리얼도 공식 문서에 두고 있어요.
- **5.2와의 관계:** 지금 스펙(서명된 웹훅으로 연결)은 A·B 어느 쪽에서도 그대로예요. B는 GitHub이 Setup URL 문서에서 권하는 확인 방법(설치한 사용자의 토큰으로 그 설치가 그 사용자 것인지 확인)을 나중에 더할 수 있게 남겨 둬요. A의 OAuth 토큰으로는 GitHub App 설치를 확인할 수 없을 것으로 보여요 (추측).
- **C를 고르지 않는 이유:** 5.2의 Setup URL 흐름을 바꿔야 하고, Spring의 기본 로그인 처리와 맞지 않을 수 있어요 (추측). 웹훅 연결이 이미 안전한 연결 방법이라 지금 얻는 게 작아요.

**B의 남는 위험과 줄이는 방법**

- 토큰 범위가 A보다 넓어요 → 저장·로그 금지(S04 수용 기준), 8시간 만료 유지, 필요하면 즉시 취소 (아래).
- 동의 화면의 '대신 행동' 문구가 사용자를 놀라게 할 수 있어요. 스펙은 랜딩에 "받는 정보: GitHub ID·이름·프로필 사진뿐"을 보여 주기로 했는데(product.md 2.5), B에서는 이 안내와 동의 화면의 인상이 다를 수 있어요. 서비스가 받아 저장하는 건 B에서도 그대로 ID·이름·프로필 사진뿐이에요. 하지만 동의 화면에는 앱 권한과 '대신 행동' 문구가 뜨고, 설치한 뒤에는 토큰이 레포 내용 읽기까지 닿아요. 랜딩 문구를 그대로 둘지, '읽기 권한만, 토큰은 저장하지 않음' 같은 설명을 더할지는 사람이 정할 일이에요 (아래 표의 product.md 행). 실제 동의 화면 문구는 H-02 실제 확인 때 봐요.
- 로그인과 설치가 한 앱에 묶여요 → GitHub App 권한을 늘리는 변경은 설치 승인과 함께 로그인 동의 화면에도 영향을 준다는 점을 권한 변경 때 확인해요 (권한은 4.6의 최소 권한이 기본이에요).

**양쪽 공통 (어느 쪽을 골라도 S04에서 판단 기록으로 정할 것)**

- **토큰을 남기지 않는 구성:** Spring Boot는 `InMemoryOAuth2AuthorizedClientService`를 자동 구성하고, 이건 받은 토큰을 메모리에 들고 있어요 (Boot 4.1 문서는 개발 환경용이라고 해요). 스펙의 '저장하지 않음'을 지키려면 로그인 뒤 인가된 클라이언트(토큰)를 보관하지 않는 구성이 필요해요. `JdbcOAuth2AuthorizedClientService`나 세션에 넣는 저장소는 쓰지 않아요.
- **PKCE:** GitHub은 두 앱 모두 `code_challenge`(S256만)를 "Strongly recommended"로 둬요. Spring Security 7.x는 `authorization_code` 클라이언트의 `ClientRegistration.clientSettings.requireProofKey` 기본값이 `true`라, client secret이 있어도 PKCE를 붙여요. 6.5까지는 기본값이 `false`였어요. 공식 문서도 제공자가 confidential client의 PKCE를 지원하지 않을 때만 `false`로 끄라고 해요. GitHub은 지원하므로 끄지 않고, S04에서 인가 요청에 PKCE가 붙는지 테스트로 확인해요.
- **즉시 취소 (선택):** `DELETE /applications/{client_id}/token`(client ID·secret으로 Basic 인증)은 OAuth App 토큰(`gho_`)과 GitHub App 사용자 토큰(`ghu_`) 모두에 써요. `GET /user` 직후 부르면 토큰 수명이 사실상 로그인 요청 하나로 줄어요. 실패해도 로그인은 실패시키지 않는 쪽이 맞아 보여요. 요청이 하나 늘어나는 비용과 함께 S04에서 정해요.
- **사용자 식별:** `CommonOAuth2Provider.GITHUB`의 `userNameAttributeName`은 `id`예요. 그래서 인증 주체 이름이 GitHub 숫자 ID가 되고, `github_id`와 바로 맞아요.
- **콜백 URL:** Spring 기본은 `{baseUrl}/{action}/oauth2/code/{registrationId}`라 `/login/oauth2/code/github`이 돼요. Nginx가 넘기는 `/login/*`(architecture 4.1)과 맞아요.

**추천안을 받아들이면 docs PR로 고칠 문서 (사람이 정한 뒤)**

| 문서 | 바뀌는 곳 |
|---|---|
| `docs/decisions/README.md` | OD-02 행 '확정'과 결정, 제안서 목록 005, ADR 표기 표의 ADR-002 |
| `docs/spec/external-systems.md` 4.6 | 마지막 항목(결정)을 결정 내용으로 바꿈. 콜백 URL(`https://(도메인)/login/oauth2/code/github`), 'Expire user authorization tokens' 켜 두기, 'Request user authorization (OAuth) during installation' 끄기 항목 추가 |
| `docs/spec/infra.md` 9.6 비밀 표 | 'GitHub OAuth client secret' → 'GitHub App client secret' |
| `docs/spec/architecture.md` 기술 스택 '로그인' 행 | "세부 방식은 OD-02" → 확정 |
| `docs/spec/api.md` 머리 안내 | "OD-02가 정해지기 전에는 4.5를 구현하지 않아요" 정리 |
| `docs/spec/processes/5.02-github-connection.md` '주의할 것' | "(ADR-002)" → 결정 제안서 005 |
| `docs/steps/README.md` | '사람 할 일' 표: H-03을 H-02에 합치거나, H-03을 '개발용 GitHub App에 콜백 URL·client secret 설정'으로 바꿈 (어느 쪽인지는 사람이 정함). 어느 쪽이든 개발용 App 등록이 S07보다 앞인 S04의 실제 로그인 확인 때 필요해져서, H-02의 '필요한 때' 칸에 S04를 더함. 합치면 로드맵 S04 행의 '사람 할 일' 칸(H-03)도 바뀜 |
| `docs/steps/S04-login-session.md` | 머리 안내, '사람 할 일', '만들 것' 2(어느 앱의 client ID를 쓰는지), '읽을 문서'에 005 '결정'(과 '양쪽 공통'), '판단 기록이 필요한 곳'에 '양쪽 공통' 항목(토큰을 남기지 않는 구성, PKCE가 붙는지 확인, 즉시 취소 여부) |
| `docs/spec/product.md` 2.5 (검토 대상) | 랜딩의 "받는 정보: GitHub ID·이름·프로필 사진뿐"을 그대로 둘지, 동의 화면에 뜨는 권한 설명을 더할지 사람이 정함. 정한 경우에만 고침 |

**다른 선택지를 고르면 바뀌는 것**

- A: H-03을 '환경별 OAuth App 등록과 client secret 보관'으로 구체화해요. 4.6 마지막 항목을 결정 내용으로 바꾸고, 9.6 비밀 표는 그대로예요. S04에서 scope를 비우는 설정을 판단 기록으로 남겨요.
- C: 5.2 그림과 처리 순서(Setup URL 대신 콜백), 4.4의 `GET /github/setup` 행, 4.6의 Setup URL 항목이 바뀌어요. S04와 S07에 GitHub이 시작한 인가를 받는 처리를 따로 만들어야 해요.

## 근거

모두 2026-11-22에 확인했어요. GitHub Docs는 버전이 없는 문서라 확인한 날짜 기준이에요.

- [GitHub Docs — Differences between GitHub Apps and OAuth apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/differences-between-github-apps-and-oauth-apps): "In general, GitHub Apps are preferred over OAuth apps", 엔터프라이즈 권한 예외, OAuth App은 조직 애플리케이션 정책을 받고 GitHub App은 받지 않음, OAuth 웹 흐름이 GitHub App user access token 흐름과 같음.
- [GitHub Docs — Deciding when to build a GitHub App](https://docs.github.com/en/apps/creating-github-apps/about-creating-github-apps/deciding-when-to-build-a-github-app): 같은 권장 문장, OAuth App 토큰은 사용자가 취소할 때까지 만료되지 않음.
- [GitHub Docs — Generating a user access token for a GitHub App](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app): 웹 흐름 엔드포인트와 파라미터, `code_challenge`(S256만) "Strongly recommended", `scope`는 권한을 정하지 않음, 토큰 권한은 사용자와 앱이 함께 가진 것만, 기본 8시간 만료, '설치 중 사용자 인증 요청' 뒤의 리다이렉트, `GET /user/installations`로 접근 가능한 설치 확인.
- [GitHub Docs — Authenticating with a GitHub App on behalf of a user](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-with-a-github-app-on-behalf-of-a-user): 앱이 설치된 계정의 리소스만, 설치하지 않아도 사용자가 인증할 수 있음.
- [GitHub Docs — Refreshing user access tokens](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens): 리프레시 토큰 6개월, 만료는 기본으로 켜짐, GitHub의 권장.
- [GitHub Docs — Building a "Login with GitHub" button with a GitHub App](https://docs.github.com/en/apps/creating-github-apps/writing-code-for-a-github-app/building-a-login-with-github-button-with-a-github-app): GitHub App으로 로그인을 만드는 공식 튜토리얼.
- [GitHub Docs — Authorizing GitHub Apps](https://docs.github.com/en/apps/using-github-apps/authorizing-github-apps): 동의 화면의 세 항목('Verify your GitHub identity', 'Know which resources you can access', 'Act on your behalf')의 뜻, 설치와 인증은 따로.
- [GitHub Docs — Reviewing and revoking authorization of GitHub Apps](https://docs.github.com/en/apps/using-github-apps/reviewing-and-revoking-authorization-of-github-apps): 사용자가 'Authorized GitHub Apps'에서 취소.
- [GitHub Docs — About the setup URL](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-setup-url): `installation_id` 위조 가능, 사용자 토큰으로 설치가 그 사용자 것인지 확인하라는 권장.
- [GitHub Docs — About the user authorization callback URL](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/about-the-user-authorization-callback-url): 콜백 URL 10개까지, 와일드카드를 끄면 정확히 일치해야 함.
- [GitHub Docs — Registering a GitHub App](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/registering-a-github-app): 'Expire user authorization tokens'(켜 두기를 강하게 권장), 'Request user authorization (OAuth) during installation'을 켜면 Setup URL을 입력할 수 없음.
- [GitHub Docs — Creating an OAuth app](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app): 콜백 URL 10개까지.
- [GitHub Docs — Authorizing OAuth apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps): 웹 흐름 파라미터, PKCE(S256만) "Strongly recommended", 토큰을 받을 때마다 신원을 다시 확인.
- [GitHub Docs — Scopes for OAuth apps](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/scopes-for-oauth-apps): scope 없음 = 공개 정보 읽기, `read:user` = 프로필 데이터 읽기, GitHub App은 scope를 쓰지 않음.
- [GitHub Docs — Token expiration and revocation](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/token-expiration-and-revocation): 1년 동안 안 쓴 OAuth 토큰 자동 취소, 사용자·앱·scope 조합당 10개, GitHub App 사용자 토큰 8시간.
- [GitHub REST — OAuth authorizations: Delete an app token](https://docs.github.com/en/rest/apps/oauth-applications#delete-an-app-token): `gho_`·`ghu_` 두 종류 모두, client ID·secret으로 Basic 인증.
- [GitHub REST — List app installations accessible to the user access token](https://docs.github.com/en/rest/apps/installations#list-app-installations-accessible-to-the-user-access-token), [Get the authenticated user](https://docs.github.com/en/rest/users/users#get-the-authenticated-user): `id`·`login`·`avatar_url`은 공개 응답에도 있음.
- [Spring Security 7.1 — OAuth 2.0 Login Core Configuration](https://docs.spring.io/spring-security/reference/7.1/servlet/oauth2/login/core.html): `CommonOAuth2Provider`가 GitHub 기본값을 미리 정함, 기본 리다이렉트 URI 템플릿.
- [Spring Security 7.1 — Authorization Grant Support (PKCE)](https://docs.spring.io/spring-security/reference/7.1/servlet/oauth2/client/authorization-grants.html): PKCE는 공개 클라이언트이거나 `requireProofKey`가 `true`일 때 적용. 제공자가 confidential client의 PKCE를 지원하지 않으면 `requireProofKey`를 `false`로 끄라고 함.
- [Spring Security `ClientRegistration` 소스 (태그 7.1.3)](https://github.com/spring-projects/spring-security/blob/7.1.3/oauth2/oauth2-client/src/main/java/org/springframework/security/oauth2/client/registration/ClientRegistration.java): `ClientSettings.Builder`의 `requireProofKey` 기본값 `true`. `authorization_code`가 아닌 grant에서만 `false`로 바꿈. 6.5.0 태그는 기본값 `false`. [`DefaultOAuth2AuthorizationRequestResolver` (7.1.3)](https://github.com/spring-projects/spring-security/blob/7.1.3/oauth2/oauth2-client/src/main/java/org/springframework/security/oauth2/client/web/DefaultOAuth2AuthorizationRequestResolver.java)는 `isRequireProofKey()`면 PKCE를 붙임.
- [Spring Security `CommonOAuth2Provider` 소스 (7.1.x 브랜치)](https://github.com/spring-projects/spring-security/blob/7.1.x/config/src/main/java/org/springframework/security/config/oauth2/client/CommonOAuth2Provider.java): GITHUB는 scope `read:user`, `userNameAttributeName` `id`, `client_secret_basic`.
- [Spring Security 7.1 — OAuth 2.0 Testing (MockMvc)](https://docs.spring.io/spring-security/reference/7.1/servlet/test/mockmvc/oauth2.html): `oauth2Login()`으로 로그인한 사용자를 흉내 냄.
- [Spring Boot 4.1 — OAuth2](https://docs.spring.io/spring-boot/4.1/reference/security/oauth2.html): `spring.security.oauth2.client.registration.github.*`, `InMemoryOAuth2AuthorizedClientService` 자동 구성(개발 환경용이라고 함).

**확인하지 못한 것 (추측)**

- GitHub App 사용자 토큰으로 `GET /user`를 부를 때 따로 필요한 권한이 있는지. 문서에는 공개 프로필을 가져올 수 있다는 것('Verify your GitHub identity')까지만 있어요
- 설치하기 전에 받은 GitHub App 사용자 토큰이 정확히 무엇에 닿는지 (문서의 규칙에서 추론)
- OAuth App을 scope 없이 쓸 때의 동의 화면 문구, 조직 접근 제한이 로그인에 영향을 주지 않는지
- OAuth 토큰으로 GitHub App 설치(`GET /user/installations`)를 확인할 수 없는지
- C에서 콜백에 오는 쿼리 파라미터(`state`, `installation_id`, `setup_action`)와, Spring 기본 로그인 처리가 받지 못하는지
- (A일 때) Spring Boot 속성으로 scope를 비울 수 있는지, 아니면 `ClientRegistrationRepository`를 직접 만들어야 하는지
- GitHub에 이미 로그인되어 있고 인증한 적이 있을 때 동의 화면 없이 바로 돌아오는지

## 검증 방법

- **S04 테스트 (어느 쪽이든):** 콜백을 거치면 `users`가 `github_id` 기준으로 생기거나 갱신됨(수용 기준). 인가 요청 리다이렉트에 `code_challenge`와 `code_challenge_method=S256`이 붙는지. 토큰이 DB(`users`, 세션 테이블)와 로그에 없는지(수용 기준). 실제 GitHub에는 접속하지 않고, 콜백 흐름은 토큰·사용자 엔드포인트를 WireMock으로 흉내 내는 방식을 S04에서 검토해요.
- **'추측' 항목:** (A일 때) scope 비우는 설정 방법은 S04 구현에서 확인해 판단 기록에 적어요. PKCE는 기본값으로 붙는지 위 테스트로 확인해요.
- **실제 확인 (사람, H-02·H-03):** 개발용 앱으로 로그인해서 동의 화면 문구, 토큰 응답의 `expires_in`(B면 28800), 두 번째 로그인 때 동의 화면이 다시 뜨는지를 봐요.
- **다시 열 조건:** GitHub이 GitHub App 사용자 인증의 동작(만료, 권한 모델)을 바꿀 때. 동의 화면 때문에 가입이 줄어드는 게 운영 데이터로 보일 때. 앱(P1 이후) 로그인을 정할 때.

<!-- 여기부터 사람이 정해요. 사람이 요청하면 에이전트가 받아 적고, '결정' 절 맨 위에 그렇게 표시해요 (docs/workflow.md 3장). 요청이 없으면 에이전트는 비워 둬요 -->

## 결정

## 검토 메모
