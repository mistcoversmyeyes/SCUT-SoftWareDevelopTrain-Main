#!/usr/bin/env bash
# =============================================================================
# 启动 MySQL 容器 (仅在 Ubuntu 虚拟机中运行!)
# =============================================================================
# 架构说明:
#   - MySQL Docker 容器跑在 Ubuntu VM 里
#   - 后端 (Spring Boot :8080) 和前端 (Vite :5173) 跑在 Windows 主机
#   - 本脚本只在 VM 内执行，主机上的启动脚本不会调用它
#
# 用法 (在 Ubuntu VM 终端中):
#   cd /path/to/SCUT-SoftWareDevelopTrain-Main
#   bash scripts/start-mysql.sh
# =============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."

echo "=== Starting MySQL 8.0 container ==="
docker-compose up -d mysql

echo -n "Waiting for MySQL to be ready"
for i in $(seq 1 30); do
  if docker-compose exec mysql mysqladmin ping -h 127.0.0.1 -uroot -proot >/dev/null 2>&1; then
    docker-compose exec mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS scut_wms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    docker-compose exec mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS scut_wms_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo ""
    echo "=== MySQL is ready at 127.0.0.1:3306 ==="
    echo "  Database: scut_wms"
    echo "  Test DB:  scut_wms_test"
    exit 0
  fi
  echo -n "."
  sleep 2
done

echo ""
echo "[ERROR] MySQL did not become ready within 60 seconds" >&2
exit 1
