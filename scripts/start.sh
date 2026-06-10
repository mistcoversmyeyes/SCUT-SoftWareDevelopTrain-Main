#!/usr/bin/env bash
# Start WMS project - backend and frontend

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Starting WMS MySQL (Docker :3306) ==="
"$PROJECT_ROOT/scripts/start-mysql.sh"

echo "=== Starting WMS Backend (Spring Boot :8080) ==="
cd "$PROJECT_ROOT/backend"
nohup mvn spring-boot:run -Dspring-boot.run.profiles=local -q > /tmp/backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID"

echo "=== Starting WMS Frontend (Vite :5173) ==="
cd "$PROJECT_ROOT/frontend"
npm install --silent 2>&1
nohup npm run dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID"

echo ""
echo "=== Services starting ==="
echo "Backend:  http://localhost:8080  (log: /tmp/backend.log)"
echo "Frontend: http://localhost:5173  (log: /tmp/frontend.log)"
echo ""
echo "Check status:"
echo "  tail -f /tmp/backend.log"
echo "  tail -f /tmp/frontend.log"
echo ""
echo "Stop services:"
echo "  kill $BACKEND_PID $FRONTEND_PID"
