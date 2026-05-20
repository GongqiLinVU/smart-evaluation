#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$PROJECT_DIR/.env"
PID_DIR="$PROJECT_DIR/.pids"

mkdir -p "$PID_DIR"

# Load environment variables
if [ -f "$ENV_FILE" ]; then
  echo "Loading configuration from .env"
  set -a
  source "$ENV_FILE"
  set +a
else
  echo "Warning: .env file not found. Copy config.txt to .env and fill in your values."
  echo "Using defaults..."
fi

export MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export FRONTEND_PORT="${FRONTEND_PORT:-5173}"

echo "============================================"
echo "  Smart Evaluation System — Starting"
echo "============================================"

# --- Backend ---
echo ""
echo "[Backend] Starting Spring Boot on port $SERVER_PORT..."
cd "$PROJECT_DIR/backend"
./mvnw spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=$SERVER_PORT" \
  -Dspring-boot.run.jvmArguments="-DMYSQL_PASSWORD=$MYSQL_PASSWORD" \
  > "$PROJECT_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$PID_DIR/backend.pid"
echo "[Backend] PID: $BACKEND_PID (log: backend.log)"

# Wait for backend to be ready
echo "[Backend] Waiting for backend to start..."
for i in $(seq 1 60); do
  if curl -s "http://localhost:$SERVER_PORT/api/auth/login" > /dev/null 2>&1 || \
     curl -s -o /dev/null -w "%{http_code}" "http://localhost:$SERVER_PORT/api/auth/login" 2>/dev/null | grep -q "4[0-9][0-9]"; then
    echo "[Backend] Ready!"
    break
  fi
  if [ $i -eq 60 ]; then
    echo "[Backend] Warning: backend may not be ready yet. Check backend.log for errors."
  fi
  sleep 2
done

# --- Frontend ---
echo ""
echo "[Frontend] Starting Vite dev server on port $FRONTEND_PORT..."
cd "$PROJECT_DIR/frontend"
if [ ! -d "node_modules" ]; then
  echo "[Frontend] Installing dependencies..."
  npm install
fi
npx vite --port "$FRONTEND_PORT" > "$PROJECT_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$PID_DIR/frontend.pid"
echo "[Frontend] PID: $FRONTEND_PID (log: frontend.log)"

echo ""
echo "============================================"
echo "  All services started!"
echo "  Backend:  http://localhost:$SERVER_PORT"
echo "  Frontend: http://localhost:$FRONTEND_PORT"
echo "============================================"
echo ""
echo "To stop: ./stop.sh"
