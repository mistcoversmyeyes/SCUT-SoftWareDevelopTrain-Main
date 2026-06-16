# Task 3: Layer 2 — API & Service Layer Report

## Status
**ALL 10 SUBTASKS COMPLETE**

## Implementation Summary

### Subtask 1: MaterialContainerTypeMapper
- Created `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerTypeMapper.java`
- Extends `BaseMapper<MaterialContainerType>`, annotated with `@Mapper`

### Subtask 2: Material-Container Association — GET endpoint
- Created `MaterialContainerTypeResponse` record in masterdata package
- Added `getMaterialContainerTypes(Long materialId)` to `MasterDataService`
- Injected `MaterialContainerTypeMapper` into `MasterDataService` constructor
- Added `GET /api/materials/{id}/container-types` to `MasterDataController`

### Subtask 3: Material-Container Association — PUT endpoint
- Created `MaterialContainerTypeUpdateRequest` record in masterdata package
- Added `updateMaterialContainerTypes(Long materialId, MaterialContainerTypeUpdateRequest)` to `MasterDataService` with `@Transactional`
- Full-replace logic: validates all IDs exist, deletes old associations, inserts new ones
- Added `PUT /api/materials/{id}/container-types` to `MasterDataController`

### Subtask 4: Container type validation on inbound order create/update
- Injected `MaterialContainerTypeMapper` into `InboundOrderService` constructor
- Added D23 validation in `validateRequest()`: ensures each line's `containerTypeId` is associated with its `materialId` in `material_container_type` table

### Subtask 5: Kanban Cancel — single endpoint
- Added `selectByIdForUpdate(Long id)` to `KanbanBoardMapper` (custom `@Select` with `FOR UPDATE`)
- Added `cancelKanban(Long kanbanId)` to `InventoryService` with `@Transactional`
- Validates kanban exists, status is `PRINTED`, and inbound order status allows cancellation
- Calls `recalcPlannedQtyAndRefreshStatus` after cancelling
- Added `POST /api/inventory/kanbans/{kanbanId}/cancel` to `InventoryController`

### Subtask 6: Kanban Cancel — batch endpoint
- Added `cancelKanbansBatch(List<Long> ids)` to `InventoryService` with `@Transactional`
- Validates all kanbans exist and are in printable status
- Tracks affected line IDs and recalculates planned qty for each
- Added `POST /api/inventory/kanbans/cancel` to `InventoryController`

### Subtask 7: recalcPlannedQtyAndRefreshStatus helper method
- Added private `recalcPlannedQtyAndRefreshStatus(Long lineId, Long orderId)` to `InventoryService`
- Sums non-cancelled kanban board quantities as new `plannedQty` on the line
- Calls existing `refreshOrderStatus()` to update order status

### Subtask 8: Release response enhancement
- Injected `KanbanBoardMapper` into `InboundOrderController`
- Modified `release()` to return `Map` with `order`, `kanbanCount`, and `kanbanCodes`
- Updated test assertions to use `$.order.status` and `$.order.releasedAt`

### Subtask 9: MasterDataOptions — verify no containerTypeId leak
- Verified: `MasterDataService.materialOptions()` returns `OptionItem(id, code, name)` — no `containerTypeId` field

### Subtask 10: data.sql seed
- Updated material INSERT to remove `container_type_id` column reference
- Added `material_container_type` seed data for materials 1-3

## Test Results
```
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Files Modified
- `backend/src/main/java/com/scut/wms/inbound/InboundOrderController.java` — release() returns Map with kanban metadata
- `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java` — inject MaterialContainerTypeMapper, D23 validation
- `backend/src/main/java/com/scut/wms/inbound/KanbanBoardMapper.java` — add selectByIdForUpdate
- `backend/src/main/java/com/scut/wms/inventory/InventoryController.java` — add kanban cancel endpoints
- `backend/src/main/java/com/scut/wms/inventory/InventoryService.java` — add cancelKanban, cancelKanbansBatch, recalc helper, CANCELLED constant
- `backend/src/main/java/com/scut/wms/masterdata/MasterDataController.java` — add GET/PUT container-types endpoints, List import
- `backend/src/main/java/com/scut/wms/masterdata/MasterDataService.java` — inject MaterialContainerTypeMapper, getMaterialContainerTypes, updateMaterialContainerTypes
- `backend/src/main/resources/data.sql` — fix material INSERT, add material_container_type seed
- `backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java` — seed material_container_type data, fix release assertions

## Files Created
- `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerTypeMapper.java`
- `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerTypeResponse.java`
- `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerTypeUpdateRequest.java`

## Commit Message
```
feat: Layer 2 — material-container association CRUD, kanban cancel API, release response enhancement

- Material-container type association: GET/PUT /api/materials/{id}/container-types
- Kanban cancel: POST /api/inventory/kanbans/{kanbanId}/cancel and batch cancel
- Container type validation on inbound order create/update (material_container_type check)
- Release endpoint returns kanbanCount + kanbanCodes metadata
- Seed data: material_container_type associations + fix material INSERT
```

## Concerns
1. **Test data coupling**: InboundOrderControllerTest seeds `material_container_type` in `@BeforeEach` because `data.sql` is not loaded in test mode (`spring.sql.init.mode=never`). Tests are self-contained via `@Transactional`.
2. **KanbanBoardMapper.selectByIdForUpdate**: Had to be added as a custom `@Select` method since MyBatis-Plus `BaseMapper` doesn't provide it by default. Follows the same pattern as `InboundOrderMapper.selectByIdForUpdate`.
3. **Release response breaking change**: The release endpoint now returns `{ "order": {...}, "kanbanCount": N, "kanbanCodes": [...] }` instead of the flat `InboundOrderResponse` JSON. Existing test assertions were updated accordingly.
