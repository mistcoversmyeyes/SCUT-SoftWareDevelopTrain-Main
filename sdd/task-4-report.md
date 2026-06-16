# Task 4 Report — Layer 3: Frontend Basic Interaction

## Status: COMPLETE

## Build: SUCCESS (0 errors)

## Files Modified

### API Layer
- `frontend/src/api/inventory.js` — Added `locationId` param to `scanInbound`, added `cancelKanban`, `cancelKanbansBatch`
- `frontend/src/api/masterData.js` — Added `fetchMaterialContainerTypes`, `updateMaterialContainerTypes`

### View Changes
1. **InboundOrderFormView.vue** — Added container type `<el-table-column>` between material and planned qty; `onMaterialChange` fetches container types on material select; auto-selects single/checks default; shows capacity info; includes `containerTypeId` in payload
2. **InboundOrderListView.vue** — Added "部分取消" button (RELEASED/PARTIAL_RECEIVED only); dialog shows kanbans with multi-select checkboxes (RECEIVED greyed out/non-selectable); calls `cancelKanbansBatch`; updated `handleRelease` to show `kanbanCount` and `kanbanCodes` from new response shape
3. **InboundScanView.vue** — Added "目标库位" `<el-select>` below kanban input (optional, placeholder "留空则按计划库位入库"); loads location options from master data via `fetchMasterDataOptions`; passes `selectedLocationId` to `scanInbound`
4. **InventoryBalanceView.vue** — Added "库存详情" column showing `{{ usedBoxes }} 箱 ({{ totalPieces }} 件) / {{ maxCapacity }} 箱`
5. **InventoryTraceView.vue** — Added "计划库位" and "实际库位" columns; shows "—" for outbound; INBOUND_RECEIVE rows with planned ≠ actual location get `warning-row` CSS class (amber highlight)
6. **KanbanListView.vue** — Added "容器类型" column displaying `containerTypeName` (falls back to "—")
7. **MaterialListView.vue** — Removed "容器类型" column from table; removed `containerTypeId` from create/edit form, model, and payload; added "包装" button; added packaging association drawer with checkbox list of ENABLED container types; shows "(默认)" label and capacity info; calls `fetchMaterialContainerTypes`/`updateMaterialContainerTypes`

## Key Details
- Backend endpoints pre-existed; all changes are pure frontend
- `containerTypeId` removed from MaterialRequest payloads (MaterialListView form/payload)
- Release response shape: now `{ order, kanbanCount, kanbanCodes }` handled in `handleRelease`
- All API imports use existing `http` wrapper from `@/api/http`
