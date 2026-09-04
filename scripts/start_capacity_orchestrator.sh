#!/bin/bash
# Start capacity test orchestrator daemon (loads QTP_API_KEY from shell env).
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${QTP_API_KEY:-}" ]]; then
  echo "QTP_API_KEY is not set. Export it or add to ~/.zshrc" >&2
  exit 1
fi
mkdir -p scripts/feishu_pending
# Stop legacy per-task poll scripts
pkill -f "poll_task.py" 2>/dev/null || true
pkill -f "capacity_orchestrator.py" 2>/dev/null || true
sleep 1
nohup python3 scripts/capacity_orchestrator.py >> scripts/capacity_orchestrator.log 2>&1 &
echo "capacity_orchestrator PID=$!"
