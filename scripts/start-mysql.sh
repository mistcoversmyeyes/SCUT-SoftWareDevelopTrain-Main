#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
docker compose up -d mysql
for _ in {1..30}; do
  if docker compose exec mysql mysqladmin ping -h 127.0.0.1 -uroot -proot >/dev/null 2>&1; then
    docker compose exec mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS scut_wms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    docker compose exec mysql mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS scut_wms_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo "MySQL is ready at 127.0.0.1:3306/scut_wms"
    exit 0
  fi
  sleep 2
done

echo "MySQL did not become ready within 60 seconds" >&2
exit 1
