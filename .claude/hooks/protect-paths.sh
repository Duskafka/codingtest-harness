#!/bin/bash
# PreToolUse 훅: 에이전트가 스펙과 하네스 파일을 실수로 고치지 못하게 막는다.
#
# 막는 경로
#   docs/harness-log.md           하네스 변경 기록 (사람이 고침)
#   CLAUDE.md, .claude/**         하네스 설정 (사람이 고침)
#   src/main/resources/db/migration/**  origin/master에 이미 있는 마이그레이션
#   docs/spec/**, docs/decisions/README.md
#                                 docs 브랜치가 아니면 막음 (사람이 정한 결정의 반영만 docs 브랜치에서)
#   docs/·루트 README.md 밖의 파일  docs 브랜치에서는 막음 (docs 브랜치는 문서 전용)
#
# docs 브랜치 = 이름이 docs/로 시작하고 단계 브랜치(docs/S<번호>-…)가 아닌 브랜치 (docs/workflow.md 4장)
#
# 사람이 Claude 세션으로 스펙을 고칠 때는 환경 변수 HARNESS_ALLOW_PROTECTED=1로 끈다.
# 막을 때는 exit 2와 함께 stderr로 이유를 돌려준다 (Claude가 읽는다).
set -u

[ "${HARNESS_ALLOW_PROTECTED:-}" = "1" ] && exit 0
# jq가 없으면 막지 않는다. CLAUDE.md의 규칙과 spec-reviewer가 남는다.
command -v jq >/dev/null 2>&1 || exit 0

input=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<<"$input" 2>/dev/null)
[ -z "$file" ] && exit 0

root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
case "$file" in
  /*) ;;
  *) file="$root/$file" ;;
esac
abs=$(realpath -m "$file" 2>/dev/null || printf '%s' "$file")
rootabs=$(realpath -m "$root" 2>/dev/null || printf '%s' "$root")

case "$abs" in
  "$rootabs"/*) rel="${abs#"$rootabs"/}" ;;
  *) exit 0 ;;
esac

block() {
  {
    echo "보호된 경로라 고칠 수 없어요: $rel"
    echo "$1"
    echo "스펙이나 하네스를 바꿔야 한다면 PR 본문의 '스펙 변경 제안'에 적어 주세요 (docs/workflow.md 4장)."
  } >&2
  exit 2
}

# 현재 브랜치가 docs 브랜치인지. 분리된 HEAD("HEAD")나 git이 없을 때(빈 값)는 아니라고 본다.
is_docs_branch() {
  case "$(git -C "$rootabs" rev-parse --abbrev-ref HEAD 2>/dev/null)" in
    docs/S[0-9]*) return 1 ;;
    docs/*) return 0 ;;
    *) return 1 ;;
  esac
}

case "$rel" in
  docs/spec/*|docs/decisions/README.md)
    is_docs_branch || block "스펙과 결정 대장은 단계·구현 PR에서 고치지 않아요. 바꿔야 한다면 PR의 '스펙 변경 제안'에 적고, 브랜치를 바꿔 피하지 않아요. 사람이 정한 결정의 반영만 docs 브랜치의 문서 전용 PR에서 해요 (docs/workflow.md 4장)." ;;
  CLAUDE.md|.claude/*|docs/harness-log.md)
    block "하네스 설정과 그 변경 기록은 사람이 고쳐요." ;;
  src/main/resources/db/migration/*)
    if git -C "$rootabs" rev-parse --verify -q origin/master >/dev/null 2>&1 \
      && git -C "$rootabs" cat-file -e "origin/master:$rel" 2>/dev/null; then
      block "master에 들어간 마이그레이션은 고치지 않아요. 새 버전 파일을 추가해요 (data-model.md 4.3 마이그레이션 원칙)."
    fi ;;
esac

# docs 브랜치는 문서 전용이다. docs/와 루트 README.md 밖은 고치지 않는다.
case "$rel" in
  docs/*|README.md) ;;
  *) is_docs_branch && block "docs 브랜치는 문서 전용이라 docs/와 루트 README.md 밖의 파일은 고치지 않아요. 코드 변경은 단계 브랜치에서 해요 (docs/workflow.md 4장)." ;;
esac

exit 0
