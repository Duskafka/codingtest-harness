# S07. GitHub App 연결

| 항목 | 내용 |
|---|---|
| 단계 | P1 |
| 선행 | S04, S06 |
| 결정 게이트 | 없음 |
| 사람 할 일 | H-02 (개발용 App 등록, 실제 연동 확인. 자동 테스트에는 불필요) |
| T번호 | T21 |

## 목표

멤버의 풀이 레포를 안전하게 이 서비스와 연결한다. 연결은 서명된 웹훅의 설치자 정보로만 한다.

## 읽을 문서

- `docs/spec/processes/5.02-github-connection.md` 전체
- `docs/spec/external-systems.md`: GitHub 행, 4.6 체크리스트
- `docs/spec/data-model.md`: `github_installations`, `linked_repositories`
- `docs/spec/api.md`: 4.4의 GitHub 행

## 만들 것

1. **설치 이벤트 처리기:** `installation`의 created, deleted, suspend, unsuspend. created는 `account.type == User`이고 `account.id == sender.id`일 때만 그 `github_id`의 사용자에게 연결한다.
2. **미가입자 설치:** 설치만 저장해 두고, 가입할 때 `github_id`로 연결한다.
3. **레포 이벤트 처리기:** `installation_repositories`의 added, removed. 연결이 끊기면 비활성으로 바꾼다 (알림은 S13).
4. **GitHub API 클라이언트:** App JWT(최대 10분) 생성, 설치 토큰(1시간) 발급과 만료 전 갱신 캐시. App private key는 서버 파일 경로로만 받는다 (infra.md 9.6).
5. **엔드포인트:**
    - `GET /api/github/install-url`: 서명된 1회용 state를 붙인 설치 주소
    - `GET /github/setup`: 연결 상태 안내만. 쿼리의 `installation_id`는 믿지 않는다
    - `GET /api/me/github/repositories`: 설치된 레포 목록
    - `PUT /api/me/linked-repository`: 풀이 레포 선택. 기본 브랜치를 조회해 저장, `repo_id`로 추적, 사용자당 1개
6. **레포가 하나면 자동 연결**, 여러 개면 사용자가 고른다.

## 만들지 않을 것

- push 처리 (S08)
- 연결이 끊겼을 때 사용자에게 보내는 알림 (S13)
- 계정 삭제 시 설치 삭제 (S15)

## 수용 기준

- [ ] T21: Setup URL에 남의 `installation_id`를 넣어도 연결되지 않는다
- [ ] `account.id`와 `sender.id`가 다르거나 조직 계정 설치면 사용자에게 연결하지 않는다
- [ ] 설치를 먼저 하고 나중에 가입하면 가입할 때 연결된다
- [ ] `installation_repositories` removed가 오면 그 레포의 연결이 비활성이 된다
- [ ] `installation` deleted·suspend → 연결 비활성, unsuspend → 다시 활성
- [ ] 레포 이름이 바뀌어도 `repo_id`로 같은 레포로 인식한다
- [ ] 설치 토큰은 만료 전에 갱신되고, 유효한 동안은 다시 발급하지 않는다
- [ ] GitHub API 호출은 WireMock으로 테스트하고, private key와 토큰이 로그에 남지 않는다
- [ ] T19: 이 단계의 엔드포인트에서 남의 설치·레포에 접근할 수 없다

## 주의할 것

- Setup URL의 `installation_id`는 위조될 수 있다 [S37].
- 조직 레포는 조직 관리자 승인이 필요하다. 개인 레포를 쓰도록 안내 문구를 응답에 담는다.
- 처리기 안에서 GitHub API를 부르면 웹훅 10초 제한에 걸릴 수 있다. 처리기에서 꼭 불러야 한다면 판단 기록에 이유와 제한 시간을 남긴다.

## 판단 기록이 필요한 곳

- 1회용 state의 서명·보관 방식
- 설치 토큰 캐시 위치와 갱신 시점
