#!/usr/bin/env bash
# 种子数据脚本 — 通过正常业务流程 API 生成库存 & 出库测试数据
# 前置条件：MySQL + 后端已启动，数据库已有 schema + data.sql 的基础数据
# 用法：bash scripts/seed-data.sh

set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
TOKEN="${TOKEN:-demo-token-admin}"
AUTH="Authorization: Bearer $TOKEN"
CT="Content-Type: application/json"

echo "=== 1. 扫码入库（生成 inventory_movement + inventory_balance） ==="

SCAN_INBOUND() {
  local code="$1"
  echo "  -> 扫描 $code"
  curl -s -X POST "$BASE/api/inventory/scan-inbound" \
    -H "$AUTH" -H "$CT" \
    -d "{\"kanbanCode\":\"$code\"}" | jq .
}

SCAN_INBOUND "KB:v1:IN-20260610-001:1:1"
SCAN_INBOUND "KB:v1:IN-20260610-001:2:1"

echo ""
echo "=== 2. 验证库存余额 ==="
curl -s "$BASE/api/inventory/balances" -H "$AUTH" | jq .

echo ""
echo "=== 3. 验证库存流水 ==="
curl -s "$BASE/api/inventory/movements" -H "$AUTH" | jq .

echo ""
echo "=== 4. 释放出库单 ==="
curl -s -X POST "$BASE/api/outbound-orders/1/release" -H "$AUTH" | jq .

echo ""
echo "=== 5. 开始拣货 ==="
curl -s -X POST "$BASE/api/outbound-orders/1/start-picking" -H "$AUTH" | jq .

echo ""
echo "=== 6. 出库扫码（带单出库，拣 20 件） ==="
curl -s -X POST "$BASE/api/outbound/scan" \
  -H "$AUTH" -H "$CT" \
  -d '{"kanbanCode":"KB:v1:IN-20260610-001:1:1","qty":20,"outboundOrderId":1,"outboundOrderLineId":1}' | jq .

echo ""
echo "=== 7. 再次扫码出库（拣完该看板剩余） ==="
curl -s -X POST "$BASE/api/outbound/scan" \
  -H "$AUTH" -H "$CT" \
  -d '{"kanbanCode":"KB:v1:IN-20260610-001:1:1","outboundOrderId":1,"outboundOrderLineId":1}' | jq .

echo ""
echo "=== 8. 查询 FIFO 推荐 ==="
curl -s -X POST "$BASE/api/outbound/recommend" \
  -H "$AUTH" -H "$CT" \
  -d '{"materialId":2,"warehouseIds":[1],"neededQty":30}' | jq .

echo ""
echo "=== Done. 出库单状态： ==="
curl -s "$BASE/api/outbound-orders/1" -H "$AUTH" | jq .

echo ""
echo "=== 最终库存： ==="
curl -s "$BASE/api/inventory/balances" -H "$AUTH" | jq .
