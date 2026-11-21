#!/usr/bin/env bash
# 클라우드 세션 준비 스크립트 (S01).
#
# Claude Code 클라우드 세션에서 Testcontainers가 쓸 Docker 데몬을 띄운다.
# SessionStart 훅(.claude/hooks/session-start.sh)이 세션을 시작할 때 실행한다.
#
# - CLAUDE_CODE_REMOTE가 true일 때만 동작한다. 로컬 개발 환경에서는 아무것도 하지 않는다.
# - 여러 번 실행해도 안전하다. Docker가 이미 응답하면 그대로 끝내고,
#   dockerd 프로세스가 이미 있으면 새로 띄우지 않고 준비될 때까지만 기다린다.
# - JDK 25 설치는 클라우드 환경의 설정 스크립트가 맡는다 (docs/workflow.md 8장, H-09).
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

wait_seconds=30
ready_timeout_seconds=5
log_file="${TMPDIR:-/tmp}/studybot-dockerd.log"
lock_file="${TMPDIR:-/tmp}/studybot-cloud-session-setup.lock"

# 동시에 여러 번 실행돼도 dockerd를 하나만 띄우도록 순서대로 실행한다.
# 세션 시작을 붙잡지 않도록 잠금은 최대 wait_seconds*2초만 기다린다.
# 한 번의 실행은 잠금을 길어야 wait_seconds + ready_timeout_seconds*2초쯤 쥐고 있다.
if command -v flock >/dev/null 2>&1; then
  exec 9>"$lock_file"
  if ! flock -w $((wait_seconds * 2)) 9; then
    echo "cloud-session-setup: 다른 실행이 끝나지 않아 잠금을 얻지 못했어요 ($lock_file)." >&2
    exit 1
  fi
fi

# 데몬이 연결만 받고 응답하지 않으면 docker info가 끝나지 않는다. 그래서 호출마다 시간 제한을 둔다.
# timeout(coreutils)이 없는 환경에서는 제한 없이 확인한다. 명령이 없다고 준비 확인이 늘 실패하면 안 된다.
docker_ready() {
  if command -v timeout >/dev/null 2>&1; then
    timeout "$ready_timeout_seconds" docker info >/dev/null 2>&1
  else
    docker info >/dev/null 2>&1
  fi
}

if docker_ready; then
  echo "cloud-session-setup: Docker가 이미 동작 중이에요."
  exit 0
fi

if ! command -v dockerd >/dev/null 2>&1; then
  echo "cloud-session-setup: dockerd가 없어요. 이 환경에서는 Testcontainers 테스트를 돌릴 수 없어요." >&2
  exit 1
fi

if pgrep -x dockerd >/dev/null 2>&1; then
  echo "cloud-session-setup: dockerd가 이미 떠 있어요. 준비될 때까지 기다려요."
else
  echo "cloud-session-setup: dockerd를 띄워요 (로그: $log_file)."
  # 9>&-: dockerd가 잠금 파일 디스크립터를 물려받으면 잠금이 영영 풀리지 않는다.
  nohup dockerd >"$log_file" 2>&1 9>&- &
fi

# 횟수가 아니라 시각으로 끊는다. 확인 한 번이 오래 걸려도 전체 대기는 wait_seconds 안팎이다.
deadline=$((SECONDS + wait_seconds))
while [ "$SECONDS" -lt "$deadline" ]; do
  if docker_ready; then
    echo "cloud-session-setup: Docker가 준비됐어요."
    exit 0
  fi
  sleep 1
done

echo "cloud-session-setup: ${wait_seconds}초 안에 Docker가 준비되지 않았어요. 로그를 확인하세요: $log_file" >&2
exit 1
