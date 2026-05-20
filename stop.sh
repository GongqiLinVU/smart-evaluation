#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_DIR="$PROJECT_DIR/.pids"

echo "============================================"
echo "  Smart Evaluation System — Stopping"
echo "============================================"

stop_service() {
  local name="$1"
  local pid_file="$PID_DIR/$name.pid"

  if [ -f "$pid_file" ]; then
    local pid
    pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
      echo "[$name] Stopping PID $pid..."
      kill "$pid" 2>/dev/null || true
      # Wait up to 10 seconds for graceful shutdown
      for i in $(seq 1 10); do
        if ! kill -0 "$pid" 2>/dev/null; then
          break
        fi
        sleep 1
      done
      # Force kill if still running
      if kill -0 "$pid" 2>/dev/null; then
        echo "[$name] Force killing PID $pid..."
        kill -9 "$pid" 2>/dev/null || true
      fi
      echo "[$name] Stopped."
    else
      echo "[$name] Process $pid not running."
    fi
    rm -f "$pid_file"
  else
    echo "[$name] No PID file found."
  fi
}

stop_service "frontend"
stop_service "backend"

# Clean up any orphaned Java processes for this project
JAVA_PIDS=$(pgrep -f "capstone-eval" 2>/dev/null || true)
if [ -n "$JAVA_PIDS" ]; then
  echo "[cleanup] Stopping orphaned backend processes: $JAVA_PIDS"
  echo "$JAVA_PIDS" | xargs kill 2>/dev/null || true
fi

rm -rf "$PID_DIR"

echo ""
echo "All services stopped."
