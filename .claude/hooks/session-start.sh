#!/bin/bash
# SessionStart 훅: 클라우드 세션 준비 스크립트가 있으면 실행한다.
# 스크립트(scripts/cloud-session-setup.sh)는 S01에서 만든다. 없으면 아무것도 하지 않는다.
# 세션 시작을 막지 않도록 항상 0으로 끝낸다.
set -u

script="${CLAUDE_PROJECT_DIR:-.}/scripts/cloud-session-setup.sh"
[ -f "$script" ] || exit 0

if ! bash "$script"; then
  echo "scripts/cloud-session-setup.sh가 실패했어요. docs/workflow.md 8장을 보고 직접 확인하세요." >&2
fi
exit 0
