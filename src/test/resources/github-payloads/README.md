# GitHub 웹훅 payload 샘플

웹훅 테스트가 MockMvc로 보내는 본문이에요 (testing.md 8.1 '웹훅', S06 '주의할 것'). 서명은 테스트가 테스트용 가짜 시크릿으로 만들어요.

## 출처와 상태

모두 **추측**이에요. 실제 전달을 받아 만든 게 아니라, GitHub 공식 예시 payload의 모양을 따랐어요.

- 출처: [octokit/webhooks](https://github.com/octokit/webhooks)의 `payload-examples/api.github.com/` (GitHub가 관리하는 웹훅 스키마·예시, 2026-11-23 `main`에서 받음)
- 계정·이메일·ID는 가짜 값으로 바꿨어요. 사람: `studybot-tester`(id `100000001`), 이메일 `tester@example.com`, 레포 `studybot-tester/algorithm`(id `200000001`), 설치 id `300000001`, App id `400001`, 그 밖의 레포·이슈·라벨 id는 `200000002`~, `600000001`~, `700000001`~. `node_id`는 모두 `FAKE_NODE_ID`예요.
- 이름·설명 같은 글(예: 레포 `Space`, 이슈 제목)은 원본 예시 그대로예요. 실제 사람의 정보가 아니에요.
- 실제 전달을 받으면(사람 할 일 H-02) `X-GitHub-Event`별로 이 파일들과 모양을 비교해 고쳐요.

| 파일 | 이벤트 (`X-GitHub-Event`) | 원본 예시 | 바꾼 것 |
|---|---|---|---|
| `ping.json` | `ping` | `ping/payload.json` | 원본은 레포 웹훅이라, GitHub App 웹훅 모양으로 `hook.type`을 `App`으로, `hook.app_id`를 더하고 `repository`와 레포 웹훅 URL들을 뺐어요 |
| `push.json` | `push` | `push/payload.json` | 원본은 태그 삭제(커밋 없음)라, 기본 브랜치 `main`에 백준허브 커밋 하나가 올라온 모양으로 바꿨어요. 커밋 메시지·경로는 `commit-samples/baekjoon/basic.json`과 같아요 |
| `installation.json` | `installation` (`action: created`) | `installation/created.payload.json` | 가짜 값만 |
| `installation_repositories.json` | `installation_repositories` (`action: added`) | `installation_repositories/added.payload.json` | 가짜 값만 |
| `issues.json` | `issues` (관심 없는 이벤트) | `issues/opened.payload.json` | 가짜 값만 |

## 지킬 것

- 개인정보를 넣지 않아요. 실제 전달을 샘플로 더할 때도 이메일·이름·ID를 가짜 값으로 바꿔요 (security.md 9.8).
- 파일 내용을 바꾸면 서명도 바뀌어요. 테스트는 파일을 읽은 바이트 그대로 서명하니 따로 고칠 건 없어요.
