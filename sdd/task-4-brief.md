# Task 4: Layer 3 — Frontend Basic Interaction

## Global Constraints
- Backend API is DONE. All endpoints at /api/* return the new response shapes.
- Use Vue 3 Composition API with `<script setup>`. This project uses Element Plus UI library.
- Follow the project's existing code patterns (import paths, `http` wrapper, `ElMessage` for toasts).
- Backend returns data directly (not wrapped in `{ success: true, data: ... }`).

## API Layer Changes

### masterData.js — add 2 functions
```js
export async function fetchMaterialContainerTypes(materialId) {
  const response = await http.get(`/materials/${materialId}/container-types`)
  return response.data
}

export async function updateMaterialContainerTypes(materialId, containerTypeIds) {
  const response = await http.put(`/materials/${materialId}/container-types`, { containerTypeIds })
  return response.data
}
```

Also: `createMaterial` and `updateMaterial` payloads MUST NOT include `containerTypeId` anymore.

### inventory.js — update scanInbound, add cancel
```js
// Add locationId parameter
export async function scanInbound(kanbanCode, locationId) {
  const response = await http.post('/inventory/scan-inbound', { kanbanCode, locationId })
  return response.data
}

// Add kanban cancel
export async function cancelKanban(kanbanId) {
  const response = await http.post(`/inventory/kanbans/${kanbanId}/cancel`)
  return response.data
}

export async function cancelKanbansBatch(ids) {
  const response = await http.post('/inventory/kanbans/cancel', { ids })
  return response.data
}
```

### inbound.js — release response shape changed
The `releaseInboundOrder` response is now: `{ order: InboundOrderResponse, kanbanCount: N, kanbanCodes: [...] }`
Update callers that access release response.

## View Changes

### 1. InboundOrderFormView.vue — 容器类型列
- In the detail table, add a "容器类型" `<el-table-column>` between "物料" and "计划数量"
- When a material is selected, fetch available container types via `fetchMaterialContainerTypes(materialId)`
- If empty: show `ElMessage.warning("该物料未配置包装容器，请先在基础数据中配置")` and clear the material selection
- If 1 container: auto-select it (disabled)
- If multiple: show dropdown, select `isDefault` first
- Next to the container selector, display capacity (e.g., "容量: 100件/箱")
- In the payload, include `containerTypeId` for each line

### 2. InboundOrderListView.vue — 部分取消按钮
- In the operations column (after "取消" button), add "部分取消" button
- Enable only when `row.status === 'RELEASED' || row.status === 'PARTIAL_RECEIVED'`
- Click opens a dialog showing kanbans for this order (PRINTED status only, RECEIVED greyed out)
- Support multi-select with checkboxes → confirm → call `cancelKanbansBatch(ids)`
- After success, refresh the list

### 3. InboundScanView.vue — 库位覆盖下拉
- Below the kanban code input, add a `<el-select>` for "目标库位" (optional)
- Placeholder: "留空则按计划库位入库"
- Filter locations by warehouse (get warehouse info from kanban scan result)
- Pass selected `locationId` to `scanInbound(kanbanCode, locationId)`
- If backend returns 400 for cross-warehouse, show error

### 4. InventoryBalanceView.vue — 箱数+件数
- Read the current view. It may display `usedQty` and `maxCapacity`.
- Change to display `usedBoxes` 箱 + `totalPieces` 件 format
- Label format: `{{ loc.usedBoxes }} 箱 ({{ loc.totalPieces }} 件) / {{ loc.maxCapacity }} 箱`
- The backend response now has `usedBoxes` and `totalPieces` fields instead of `usedQty`

### 5. InventoryTraceView.vue — 计划/实际库位
- For INBOUND_RECEIVE movement records, show "计划库位" and "实际库位" columns
- Take `plannedLocationCode` from response
- If planned ≠ actual, highlight the row (add CSS class `warning-row`)
- For outbound movements, show "—" in planned location column

### 6. KanbanListView.vue — 补充字段
- Add "容器类型" column (use `containerTypeName` from response)
- The "库位" column should already show location name correctly (backend now joins via kanban.location_id)

### 7. MaterialListView.vue — 包装关联入口
- In the operations column, add "包装" button (type="info", text, size="small")
- Click opens an `el-drawer` or `el-dialog`:
  - Title: "物料包装关联 — {materialCode} {materialName}"
  - Show all ENABLED container types as checkboxes (from `fetchContainerTypes`)
  - Check already-associated ones (from `fetchMaterialContainerTypes`)
  - Confirm → call `updateMaterialContainerTypes(materialId, checkedIds)`
  - Mark `isDefault=true` container with "(默认)" label
- Remove "容器类型" column from the material table (1:N relation can't be single column)
- Remove `containerTypeId` from material create/edit form

### 8. Release response update
Any code calling `releaseInboundOrder` now gets `{ order: {...}, kanbanCount: N, kanbanCodes: [...] }` instead of just the order. Update to handle this:
- Show success message: "已生成 N 个看板"
- Display kanban codes briefly or log them

## Verification
```bash
cd frontend && npm run build
```
Must succeed with no errors. If `npm run build` fails, fix the errors.

## Commit
```
feat: Layer 3 — frontend container type column, partial cancel, location override, box count

- InboundOrderForm: add container type column with dynamic dropdown
- InboundOrderList: add partial cancel button with kanban selection dialog
- InboundScan: add optional location override dropdown
- InventoryBalance: switch to usedBoxes + totalPieces display
- InventoryTrace: add planned/actual location columns with diff highlight
- KanbanList: add containerTypeName column
- MaterialList: replace containerTypeId column with packaging association drawer
- API layer: add material container type, kanban cancel, update scanInbound
- Handle new release response shape (order + kanbanCount + kanbanCodes)
```
