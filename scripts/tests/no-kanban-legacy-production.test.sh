#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PATTERN='kanban_board|kanban_code|kanban_board_id|kanbanCode|kanbanId|KanbanBoard|migrateKanban|KB:v1|KANBAN_BOARD|LOCKED_KANBAN'

if rg -n "$PATTERN" \
  "$REPO_ROOT/backend/src/main/java" \
  "$REPO_ROOT/backend/src/test/java" \
  "$REPO_ROOT/scripts/seed-data.sql" \
  "$REPO_ROOT/scripts/seed-data.sh" \
  "$REPO_ROOT/README.md" \
  "$REPO_ROOT/ARCHITECTURE.md"; then
  echo "Found legacy kanban production fields/data. Use inventory_tag/inventoryTagCode/IT:v1 only." >&2
  exit 1
fi
