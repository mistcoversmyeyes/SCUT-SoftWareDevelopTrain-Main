#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BIN="$TMP_DIR/bin"
LOG_FILE="$TMP_DIR/calls.log"
OUTPUT_FILE="$TMP_DIR/start.out"
mkdir -p "$FAKE_BIN"
: > "$LOG_FILE"

cat > "$FAKE_BIN/docker" <<'FAKE'
#!/usr/bin/env bash
exit 0
FAKE

cat > "$FAKE_BIN/docker-compose" <<'FAKE'
#!/usr/bin/env bash
echo "docker-compose $*" >> "$TEST_LOG"
exit 0
FAKE

cat > "$FAKE_BIN/mysql" <<'FAKE'
#!/usr/bin/env bash
args="$*"
if [[ "$args" == *"SELECT 1"* ]]; then
  echo "mysql:probe" >> "$TEST_LOG"
  exit 0
fi
if [[ "$args" == *"CREATE DATABASE IF NOT EXISTS scut_wms"* ]]; then
  echo "mysql:create-db" >> "$TEST_LOG"
  exit 0
fi
if [[ "$args" == *"information_schema.TABLES"* ]]; then
  echo "mysql:table-check" >> "$TEST_LOG"
  echo "1"
  exit 0
fi
if [[ "$args" == *"COUNT(*) FROM scut_wms.supplier"* ]]; then
  echo "mysql:supplier-count" >> "$TEST_LOG"
  echo "0"
  exit 0
fi

stdin_file="$(mktemp)"
cat > "$stdin_file"
if grep -q "CREATE TABLE supplier" "$stdin_file"; then
  echo "mysql:schema-import" >> "$TEST_LOG"
elif grep -q 'LOCK TABLES `container_type` WRITE' "$stdin_file"; then
  echo "mysql:seed-import" >> "$TEST_LOG"
else
  echo "mysql:unknown-import" >> "$TEST_LOG"
fi
rm -f "$stdin_file"
exit 0
FAKE

cat > "$FAKE_BIN/mvn" <<'FAKE'
#!/usr/bin/env bash
echo "mvn $*" >> "$TEST_LOG"
exit 0
FAKE

cat > "$FAKE_BIN/npm" <<'FAKE'
#!/usr/bin/env bash
echo "npm $*" >> "$TEST_LOG"
exit 0
FAKE

cat > "$FAKE_BIN/curl" <<'FAKE'
#!/usr/bin/env bash
echo "curl $*" >> "$TEST_LOG"
printf '200'
exit 0
FAKE

cat > "$FAKE_BIN/sleep" <<'FAKE'
#!/usr/bin/env bash
exit 0
FAKE

chmod +x "$FAKE_BIN"/*

TEST_LOG="$LOG_FILE" PATH="$FAKE_BIN:$PATH" bash "$REPO_ROOT/scripts/start.sh" > "$OUTPUT_FILE" 2>&1

if ! grep -q "mysql:schema-import" "$LOG_FILE"; then
  echo "Expected scripts/start.sh to reinitialize schema when supplier table exists but has no rows." >&2
  echo "Observed calls:" >&2
  cat "$LOG_FILE" >&2
  exit 1
fi

if ! grep -q "mysql:seed-import" "$LOG_FILE"; then
  echo "Expected scripts/start.sh to reload seed data when supplier table exists but has no rows." >&2
  echo "Observed calls:" >&2
  cat "$LOG_FILE" >&2
  exit 1
fi
