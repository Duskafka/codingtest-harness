#!/bin/bash
# PreToolUse 훅: 에이전트가 스펙과 하네스 파일을 실수로 고치지 못하게 막는다.
#
# 막는 경로
#   docs/spec/**                  스펙 (사람이 고침)
#   docs/decisions/README.md      결정 대장 (사람이 고침)
#   docs/harness-log.md           하네스 변경 기록 (사람이 고침)
#   CLAUDE.md, .claude/**         하네스 설정 (사람이 고침)
#   src/main/resources/db/migration/**  origin/master에 이미 있는 마이그레이션
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

case "$rel" in
  docs/spec/*)
    block "스펙 문서는 사람이 고쳐요." ;;
  docs/decisions/README.md)
    block "결정 대장은 사람이 고쳐요. 제안은 docs/decisions/NNN-제목.md 결정 제안서로 써요." ;;
  CLAUDE.md|.claude/*|docs/harness-log.md)
    block "하네스 설정과 그 변경 기록은 사람이 고쳐요." ;;
  src/main/resources/db/migration/*)
    if git -C "$rootabs" rev-parse --verify -q origin/master >/dev/null 2>&1 \
      && git -C "$rootabs" cat-file -e "origin/master:$rel" 2>/dev/null; then
      block "master에 들어간 마이그레이션은 고치지 않아요. 새 버전 파일을 추가해요 (data-model.md 4.3 마이그레이션 원칙)."
    fi ;;
esac

exit 0
