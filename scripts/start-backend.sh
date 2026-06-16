#!/usr/bin/env bash
# =============================================================================
# 启动 WMS 后端 (在 Windows 主机上运行, Git Bash)
# =============================================================================
# 前置条件: MySQL 必须在 Ubuntu VM 中已启动 (bash scripts/start-mysql.sh)
#
# 用法 (在 Windows Git Bash 中):
#   cd /c/Users/lzd/Documents/大众企业实训/SCUT-SoftWareDevelopTrain-Main
#   bash scripts/start-backend.sh
# =============================================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
JAR_FILE="$BACKEND_DIR/target/wms-week1-backend-0.0.1-SNAPSHOT.jar"
LOG_FILE="/tmp/wms-backend.log"
PORT=8080

# ---- 清理端口占用 ----
echo "[clean] Checking port $PORT..."
if netstat -ano 2>/dev/null | grep -q ":$PORT.*LISTENING"; then
    PID=$(netstat -ano 2>/dev/null | grep ":$PORT.*LISTENING" | awk '{print $NF}' | head -1)
    echo "[clean] Port $PORT is in use by PID $PID, killing..."
    taskkill //PID "$PID" //F 2>/dev/null || true
    sleep 1
    echo "[clean] Port $PORT freed"
else
    echo "[clean] Port $PORT is free"
fi

# ---- 编译 ----
echo "[build] Packaging backend (mvn package -DskipTests)..."
cd "$BACKEND_DIR"
mvn package -DskipTests -q

# ---- 启动 ----
echo "[start] Starting backend on http://localhost:$PORT ..."
cd "$BACKEND_DIR"
nohup java -jar "$JAR_FILE" --spring.profiles.active=local > "$LOG_FILE" 2>&1 &
BACKEND_PID=$!
echo "[start] Backend PID: $BACKEND_PID"
echo "$BACKEND_PID" > "$BACKEND_DIR/.pid"

# ---- 等待就绪 ----
echo -n "[wait]  Waiting for backend startup"
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/api/auth/login" \
        -X POST -H "Content-Type: application/json" \
        -d '{"username":"admin","password":"123456"}' 2>/dev/null | grep -q 200; then
        echo " OK"
        echo ""
        echo "=== Backend is ready ==="
        echo "URL:      http://localhost:$PORT"
        echo "Log:      $LOG_FILE"
        echo "PID:      $BACKEND_PID"
        exit 0
    fi
    echo -n "."
    sleep 2
done

echo ""
echo "[warn] Backend started (PID $BACKEND_PID) but health check timed out."
echo "       This is normal if MySQL is not yet available."
echo "       Check log: tail -f $LOG_FILE"
