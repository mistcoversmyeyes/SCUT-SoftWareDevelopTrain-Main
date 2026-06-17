#!/usr/bin/env bash
# =============================================================================
# SCUT WMS 一键启动脚本（Ubuntu / Windows Git Bash 通用）
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
PROJECT_ROOT="$(pwd)"

echo "============================================"
echo "  SCUT WMS 一键启动"
echo "============================================"

# ── 1. MySQL ──
echo ""
echo "[1/4] 启动 MySQL..."
if command -v docker &>/dev/null; then
  docker-compose up -d mysql 2>/dev/null || docker compose up -d mysql 2>/dev/null || true
elif command -v mysql &>/dev/null; then
  echo "  检测到本地 MySQL，跳过 docker"
else
  echo "  未检测到 MySQL，请先安装 Docker 或 MySQL 8.0"
  exit 1
fi

echo -n "  等待 MySQL 就绪"
for i in $(seq 1 30); do
  if mysql -u root -proot -h 127.0.0.1 -e "SELECT 1" &>/dev/null; then
    echo " OK"
    break
  fi
  echo -n "."
  sleep 2
done

# ── 2. 初始化数据库（仅首次）──
echo ""
echo "[2/4] 初始化数据库..."
DB_EXISTS=$(mysql -u root -proot -h 127.0.0.1 -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='scut_wms' AND TABLE_NAME='supplier'" 2>/dev/null || echo 0)
if [ "$DB_EXISTS" = "0" ]; then
  echo "  首次启动，导入种子数据..."
  mysql -u root -proot -h 127.0.0.1 scut_wms < scripts/seed-data.sql
  echo "  种子数据导入完成（DatabaseMigration 会自动建表）"
else
  echo "  数据库已有数据，跳过导入"
fi

# ── 3. 启动后端 ──
echo ""
echo "[3/4] 启动后端 (Spring Boot :8080)..."
cd "$PROJECT_ROOT/backend"
mvn spring-boot:run -DskipTests -q > /tmp/wms-backend.log 2>&1 &
BACKEND_PID=$!
echo "  PID: $BACKEND_PID"

echo -n "  等待后端就绪"
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" "http://localhost:8080/api/auth/login" \
      -X POST -H "Content-Type: application/json" \
      -d '{"username":"admin","password":"123456"}' 2>/dev/null | grep -q 200; then
    echo " OK"
    break
  fi
  echo -n "."
  sleep 3
done

# ── 4. 启动前端 ──
echo ""
echo "[4/4] 启动前端 (Vite :5173)..."
cd "$PROJECT_ROOT/frontend"
if [ ! -d "node_modules" ]; then
  echo "  安装 npm 依赖..."
  npm install --silent
fi
npm run dev -- --host 0.0.0.0 > /tmp/wms-frontend.log 2>&1 &
FRONTEND_PID=$!
echo "  PID: $FRONTEND_PID"

echo -n "  等待前端就绪"
for i in $(seq 1 15); do
  if curl -s -o /dev/null -w "%{http_code}" "http://localhost:5173" 2>/dev/null | grep -q 200; then
    echo " OK"
    break
  fi
  echo -n "."
  sleep 1
done

# ── 完成 ──
echo ""
echo "============================================"
echo "  启动完成！"
echo "============================================"
echo "  MySQL:    127.0.0.1:3306"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo ""
echo "  登录: admin / 123456"
echo ""
echo "  后端日志: tail -f /tmp/wms-backend.log"
echo "  前端日志: tail -f /tmp/wms-frontend.log"
echo "============================================"
echo ""
echo "  停止: kill $BACKEND_PID $FRONTEND_PID"
echo ""
