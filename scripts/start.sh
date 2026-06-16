#!/usr/bin/env bash
# =============================================================================
# 一键启动 WMS 项目 (在 Windows 主机上运行, Git Bash)
# =============================================================================
# 架构:
#   MySQL 容器 → Ubuntu VM  (docker-compose, 127.0.0.1:3306)
#   Spring Boot → Windows  (:8080, 本脚本启动)
#   Vite 前端   → Windows  (:5173, 本脚本启动)
#
# 前置条件:
#   1. Ubuntu VM 已启动，MySQL 容器已运行
#      → VM 中执行: bash scripts/start-mysql.sh
#   2. Windows 上已安装: Java 17, Maven, Node.js
#
# 用法:
#   bash scripts/start.sh
# =============================================================================
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
LOG_FRONTEND="/tmp/wms-frontend.log"
PORT_FRONTEND=5173

echo "============================================"
echo "  SCUT WMS 项目一键启动"
echo "============================================"
echo "  架构: MySQL(VM) | Backend(Win) | Frontend(Win)"
echo "============================================"
echo ""

# ---- 检查 MySQL ----
echo "--- [check] MySQL connectivity ---"
if curl -s telnet://127.0.0.1:3306 >/dev/null 2>&1 </dev/null; then
    echo "[check] MySQL is reachable at 127.0.0.1:3306"
else
    echo "============================================"
    echo "  [WARN] MySQL 不可达!"
    echo ""
    echo "  请先在 Ubuntu VM 中启动 MySQL:"
    echo "    cd <项目路径>"
    echo "    bash scripts/start-mysql.sh"
    echo ""
    echo "  确认 VM 的 3306 端口已转发到主机 3306"
    echo "============================================"
    echo ""
    # 不退出，让用户选择是否继续
fi

# ---- 清理前端端口 ----
echo ""
echo "--- [clean] Checking port $PORT_FRONTEND ---"
if netstat -ano 2>/dev/null | grep -q ":$PORT_FRONTEND.*LISTENING"; then
    PID=$(netstat -ano 2>/dev/null | grep ":$PORT_FRONTEND.*LISTENING" | awk '{print $NF}' | head -1)
    echo "[clean] Port $PORT_FRONTEND in use by PID $PID, killing..."
    taskkill //PID "$PID" //F 2>/dev/null || true
    sleep 1
    echo "[clean] Port $PORT_FRONTEND freed"
else
    echo "[clean] Port $PORT_FRONTEND is free"
fi

# ---- 启动后端 ----
echo ""
echo "============================================"
echo "  [1/2] Starting Backend (Spring Boot :8080)"
echo "============================================"
"$PROJECT_ROOT/scripts/start-backend.sh"

# ---- 启动前端 ----
echo ""
echo "============================================"
echo "  [2/2] Starting Frontend (Vite :5173)"
echo "============================================"
cd "$FRONTEND_DIR"

# 如果 node_modules 不存在才安装
if [ ! -d "node_modules" ]; then
    echo "[install] Installing npm dependencies..."
    npm install --silent
fi

nohup npm run dev > "$LOG_FRONTEND" 2>&1 &
FRONTEND_PID=$!
echo "[start] Frontend PID: $FRONTEND_PID"

# 等待前端就绪
echo -n "[wait]  Waiting for frontend"
for i in $(seq 1 15); do
    if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT_FRONTEND" 2>/dev/null | grep -q 200; then
        echo " OK"
        break
    fi
    echo -n "."
    sleep 1
done

# ---- 汇总 ----
echo ""
echo "============================================"
echo "  启动完成!"
echo "============================================"
echo "  MySQL:    127.0.0.1:3306 (Ubuntu VM)"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo ""
echo "  Backend log:  tail -f /tmp/wms-backend.log"
echo "  Frontend log: tail -f /tmp/wms-frontend.log"
echo "============================================"
