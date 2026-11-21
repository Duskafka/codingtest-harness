# 문서 지도

이 레포의 문서는 세 층으로 나뉘어요.

1. **`CLAUDE.md`**: 에이전트가 세션마다 읽는 진입점. 짧게 유지해요.
2. **단계 문서 (`docs/steps/`)**: 세션 하나에서 할 일. 무엇을 읽을지도 여기 적혀 있어요.
3. **스펙 (`docs/spec/`)**: 무엇을 만드는지. 단계 문서가 가리키는 절만 그때그때 읽어요.

## 누가 무엇을 고치나

| 문서 | 내용 | 고치는 사람 |
|---|---|---|
| `CLAUDE.md`, `.claude/` | 에이전트 진입점, 훅, 검토 서브에이전트 | 사람 |
| `docs/workflow.md` | 작업 방식, 판단 기록 양식, 완료 기준 | 사람 |
| `docs/harness-log.md` | 하네스를 언제, 왜 바꿨는지 | 사람 |
| `docs/spec/` | 제품·규칙·설계 (계약) | 사람 |
| `docs/decisions/README.md` | 확정 결정과 열린 결정 대장 | 사람 |
| `docs/decisions/NNN-*.md` | 결정 제안서 | 에이전트가 제안, 사람이 '결정' 칸 |
| `docs/steps/` | 단계 문서와 로드맵 | 사람이 내용, 에이전트가 상태와 체크 |
| `docs/progress.md` | 세션 인계 기록 | 에이전트 |

스펙을 고칠 때는 GitHub 웹이나 로컬 편집기에서 직접 고치는 게 가장 간단해요. Claude 세션으로 고치려면 그 세션의 환경 변수에 `HARNESS_ALLOW_PROTECTED=1`을 잠깐 넣어요 (`.claude/hooks/protect-paths.sh`).

## 스펙 문서

| 파일 | 계획서 절 | 내용 |
|---|---|---|
| `spec/product.md` | 0.3, 0.4, 2.1~2.7 | 한 줄 정의, 사용자, 기능 목록, 하지 않는 것, 화면, 알림 채널 |
| `spec/glossary.md` | — | 용어와 코드 이름, 번호 체계 |
| `spec/domain-rules.md` | 3.1~3.6 | 용어, 규칙 R1~R16, 시간 규칙, 상태 다이어그램, 판정 예시, 열린 질문 |
| `spec/architecture.md` | 4.1, 4.2, 6장 | 구성도, 모듈 구조와 의존 규칙, 기술 스택, 깊게 다룰 기술 과제, 알려진 충돌 |
| `spec/data-model.md` | 4.3 | 테이블, 제약, 인덱스, 마이그레이션 원칙 |
| `spec/api.md` | 4.4, 4.5 | 엔드포인트, 로그인, 권한 표 |
| `spec/external-systems.md` | 4.6 | 외부 시스템 제약 요약, GitHub App 설정 |
| `spec/processes/` | 5.1~5.17 | 프로세스별 처리 순서, 만들 것, 주의할 것, 테스트 |
| `spec/frontend.md` | 7장 | 프론트엔드 원칙 |
| `spec/testing.md` | 8.1, 8.2 | 테스트 층, 하네스 규칙, T1~T30 |
| `spec/infra.md` | 9.1~9.6, 9.9, 9.10 | 배포, 비용, Supabase, 운영 보안, 모니터링, 운영 전 점검표 |
| `spec/security.md` | 9.7, 9.8 | 애플리케이션 보안, 개인정보 |
| `spec/metrics.md` | 10장 | 제품 지표, 기술 지표 |
| `spec/pseudocode.md` | 부록 D | 의사코드 |
| `spec/references.md` | 부록 E | 출처 [S번호] |

## 절 번호 찾기

문서 안의 번호는 아래에서 찾아요. 파일의 제목에도 같은 번호가 있어서 `grep -rn "^#.* 9\.7 " docs/spec`처럼 찾을 수 있어요.

| 표기 | 위치 |
|---|---|
| 0.3, 0.4, 2.x | `spec/product.md` |
| 3.x, R1~R16, Q1~Q12 | `spec/domain-rules.md` |
| 4.1, 4.2, 6장 ①~⑥ | `spec/architecture.md` |
| 4.3 | `spec/data-model.md` |
| 4.4, 4.5 | `spec/api.md` |
| 4.6 | `spec/external-systems.md` |
| 5장, 5.N | `spec/processes/5.0N-*.md` (5.10부터는 `5.NN-*.md`) |
| 7장 | `spec/frontend.md` |
| 8장, 8.x, T1~T30 | `spec/testing.md` |
| 9장, 9.1~9.6, 9.9, 9.10 | `spec/infra.md` |
| 9.7, 9.8 | `spec/security.md` |
| 10장, 10.x | `spec/metrics.md` |
| 부록 D | `spec/pseudocode.md` |
| [S번호] | `spec/references.md` |
| D번호, OD번호, 'ADR-00N' | `decisions/README.md` |
| S01~S17 | `steps/` |
| H-NN | `steps/README.md`의 '사람 할 일' |

## 나중에 생길 폴더

- `docs/ops/`: 운영 지표 기록(10.3), 장애 기록, 런북(9.6). 런북은 S17에서 만들어요.
- `docs/experiments/`: 측정 비교 기록 (예: P2의 웹훅 처리 방식 비교, 6장 ①).

## 이 문서들의 출처

비공개 계획서 v1.3(2026-11-17)을 에이전트가 읽기 좋게 나눈 거예요. 시장 조사, 모집·인터뷰, 일정·주간 루틴, 중단 기준은 제품을 만드는 데 필요 없어서 넣지 않았어요. 계획서의 절 번호는 그대로 살려 두었어요.
