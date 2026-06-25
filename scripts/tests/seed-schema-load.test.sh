#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DB_NAME="scut_wms_seed_test_$$"

cleanup() {
  mysql -h 127.0.0.1 -uroot -proot -e "DROP DATABASE IF EXISTS $DB_NAME;" >/dev/null 2>&1 || true
}
trap cleanup EXIT

mysql -h 127.0.0.1 -uroot -proot -e "DROP DATABASE IF EXISTS $DB_NAME; CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null
mysql -h 127.0.0.1 -uroot -proot "$DB_NAME" < "$REPO_ROOT/backend/src/main/resources/schema.sql" >/dev/null
mysql -h 127.0.0.1 -uroot -proot "$DB_NAME" < "$REPO_ROOT/scripts/seed-data.sql" >/dev/null

supplier_count="$(mysql -h 127.0.0.1 -uroot -proot "$DB_NAME" -N -e "SELECT COUNT(*) FROM supplier;")"
inventory_tag_count="$(mysql -h 127.0.0.1 -uroot -proot "$DB_NAME" -N -e "SELECT COUNT(*) FROM inventory_tag;")"

if (( supplier_count == 0 )); then
  echo "Expected seed data to load suppliers." >&2
  exit 1
fi

if (( inventory_tag_count == 0 )); then
  echo "Expected seed data to load inventory tags." >&2
  exit 1
fi
