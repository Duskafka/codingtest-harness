# 커밋 샘플

문제 식별 파서(`ProblemParser`, 5.4)를 실제 커밋 모양으로 확인하는 샘플이에요. 이 폴더 아래의 `.json` 파일마다 `CommitSampleTest`가 테스트를 하나씩 돌려요. 샘플을 더하면 테스트가 늘어나요.

## 샘플 더하기 (사람 할 일 H-01)

1. GitHub에서 커밋을 열고 커밋 메시지와 바뀐 파일 경로를 확인해요. push 웹훅의 `commits[].message`, `commits[].added`, `commits[].modified`와 같은 값이에요.
2. 플랫폼 폴더(`baekjoon/`, `programmers/`, `leetcode/`, 그 밖은 `other/`)에 새 `.json` 파일을 만들어요. 파일 이름은 영문 소문자와 `-`로 써요.
3. `provenance`를 `"실제"`로 쓰고, `origin`에 어디서 가져온 커밋인지 적어요 (예: `"백준허브 1.4.29로 2026-12-01에 올린 커밋"`).
4. `./gradlew test --tests 'com.duskafka.studybot.verification.CommitSampleTest'`로 돌려 봐요.

개인정보를 넣지 않아요. 커밋 작성자, 이메일, 레포 주소는 적지 않고, 메시지와 경로만 적어요.

## 형식

```json
{
  "provenance": "추측",
  "origin": "샘플의 출처: 링크나 설명",
  "description": "이 샘플이 보여 주는 것 (테스트 이름에 나와요)",
  "message": "[Silver I] Title: 미로 탐색, Time: 76 ms, Memory: 14532 KB -BaekjoonHub",
  "paths": [
    "백준/Silver/2178. 미로 탐색/미로 탐색.java",
    "백준/Silver/2178. 미로 탐색/README.md"
  ],
  "expected": [
    { "platform": "BOJ", "problemNo": "2178", "title": "미로 탐색" }
  ],
  "expectedUnidentified": []
}
```

| 필드 | 필수 | 내용 |
|---|---|---|
| `provenance` | 예 | `"실제"`: 사람이 준 실제 커밋. `"추측"`: 공개 소스나 스펙을 보고 만든 것. 둘 중 하나가 아니면 테스트가 실패해요 |
| `origin` | 예 | 출처. `추측`이면 근거로 삼은 소스 링크와 버전 |
| `description` | 예 | 한 줄 설명 |
| `message` | 예 | 커밋 메시지 전체. 파서는 첫 줄만 봐요 |
| `paths` | 예 | 추가·수정된 파일 경로 목록 (삭제된 파일은 넣지 않아요) |
| `expected` | 예 | 식별해야 하는 문제 목록. 경로 목록에서 처음 나온 순서예요. 없으면 `[]` |
| `expected[].platform` | 예 | `BOJ`, `PROGRAMMERS`, `LEETCODE` |
| `expected[].problemNo` | 예 | 0으로 시작하지 않는 숫자 문자열 (LeetCode `0001` → `"1"`) |
| `expected[].title` | 아니오 | 적으면 식별한 제목과 비교해요 |
| `expectedUnidentified` | 아니오 | 식별하지 못한 풀이 파일 후보(확인 대기 후보)의 경로. 생략하면 `[]` |

- 모르는 필드가 있으면 테스트가 실패해요. 필드 이름을 틀리지 않았는지 확인해요.
- 보이지 않거나 헷갈리는 문자는 `\u` 이스케이프로 적어요. 백준허브는 제목의 공백을 U+2005(` `)로 써요. NFD 한글(macOS에서 직접 커밋한 경로)은 자모를 하나씩 적어요 (`baekjoon/nfd-path.json` 참고). 편집기가 저장하면서 NFC로 바꿀 수 있어서예요.

## 지금 있는 샘플

모두 `추측`이에요. 실제 커밋 샘플(H-01)이 아직 없어서, 2026-11-22에 아래 공개 소스를 보고 만들었어요.

- 백준허브: <https://github.com/BaekjoonHub/BaekjoonHub> master (manifest 1.4.29). 태그가 없어 master 파일을 링크했어요
- LeetHub-3.0: <https://github.com/raphaelheinz/LeetHub-3.0/tree/v0.0.16>
- LeetHub(원본): <https://github.com/QasimWani/LeetHub>
- 코드트리 GitHub 연동은 형식을 확인하지 못해 샘플이 없어요 (5.4 표의 '확인 필요')
