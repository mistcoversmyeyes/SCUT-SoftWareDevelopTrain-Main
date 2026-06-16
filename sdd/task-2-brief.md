# Task 2: Layer 1 — Core Business Logic Implementation

## Global Constraints
- D24: Full data rebuild. No NULL fallbacks in service code.
- D02: Material:Container = 1:N via material_container_type middle table
- D06: All kanbans inherit line.target_location_id on release
- D10: Force inbound allows overriding kanban location (within same warehouse, D27)
- D28: LOCKED kanbans count toward location capacity (RECEIVED + LOCKED)

## Prerequisites
- Layer 0 DDL is DONE. database has: material_container_type table, kanban_board.location_id/container_type_id, inbound_order_line.container_type_id, inventory_movement.planned_location_id
- Entity defaults: KanbanBoard.locationId=0L, containerTypeId=0L; InboundOrderLine.containerTypeId=0L

## Subtask A: InboundOrderRequest — add containerTypeId

File: `backend/src/main/java/com/scut/wms/inbound/InboundOrderRequest.java`

Add to Line record:
```java
@NotNull(message = "容器类型不能为空")
Long containerTypeId,
```

After `@NotNull Long materialId,`.

## Subtask B: InboundOrderService — insertLines + insertKanbans + toResponse

File: `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java`

### B1: Add ContainerTypeMapper dependency
Add `com.scut.wms.container.ContainerTypeMapper` to constructor injection.

### B2: insertLines() — pass containerTypeId
In `insertLines()`, add:
```java
line.setContainerTypeId(requestLine.containerTypeId());
```

### B3: insertKanbans() — complete rewrite
Replace the single-kanban-per-line loop with multi-kanban generation:

```java
private void insertKanbans(InboundOrder order, List<InboundOrderLine> lines, LocalDateTime printedAt) {
    for (InboundOrderLine line : lines) {
        // Get capacity from container type
        var ct = containerTypeMapper.selectById(line.getContainerTypeId());
        if (ct == null || ct.getCapacityQty() == null || ct.getCapacityQty().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("该物料未配置有效包装容量，请先在基础数据中配置");
        }
        int capacityQty = ct.getCapacityQty().intValue();
        int plannedQty = line.getPlannedQty().intValue();
        int fullBoxes = plannedQty / capacityQty;
        int remainder = plannedQty % capacityQty;
        int totalKanbans = fullBoxes + (remainder > 0 ? 1 : 0);

        for (int seq = 1; seq <= totalKanbans; seq++) {
            KanbanBoard board = new KanbanBoard();
            board.setKanbanCode("KB:v1:%s:%d:%d".formatted(order.getInboundNo(), line.getLineNo(), seq));
            board.setInboundOrderId(order.getId());
            board.setInboundOrderLineId(line.getId());
            board.setLocationId(line.getTargetLocationId());
            board.setContainerTypeId(line.getContainerTypeId());
            if (seq <= fullBoxes) {
                board.setBoardQty(BigDecimal.valueOf(capacityQty));
            } else {
                board.setBoardQty(BigDecimal.valueOf(remainder));
            }
            board.setStatus(PRINTED);
            board.setPrintedAt(printedAt);
            kanbanBoardMapper.insert(board);
        }
    }
}
```

IMPORTANT: `plannedQty` and `capacityQty` are BigDecimal. Use `.intValue()` after verifying the scale makes sense for integer conversion. If the values have fractional parts, throw BusinessException.

### B4: toResponse() — add container info to LineDisplay
In the `toResponse()` method's `LineDisplay` constructor, add after `line.getTargetLocationId()`:
```java
line.getContainerTypeId(),
```
And update `InboundOrderResponse.LineDisplay` constructor (see Subtask E).

## Subtask C: InventoryService — scanInbound with kanban location override + audit trail

File: `backend/src/main/java/com/scut/wms/inventory/InventoryService.java`

### C1: Add StorageLocationMapper dependency
Add `com.scut.wms.masterdata.StorageLocationMapper` to constructor injection.

### C2: Modify scanInbound() signature
The request now has optional `locationId`. Modify the method:

In `scanInbound(ScanInboundRequest request)`:
1. Load ScanKanbanContext as before
2. Determine actual location:
   ```java
   Long actualLocationId = (request.locationId() != null) ? request.locationId() : context.getTargetLocationId();
   ```
3. If actualLocationId differs from context.getTargetLocationId(), this is a FORCE inbound:
   - Validate warehouse consistency (D27): query storage_location by actualLocationId, check its warehouse_id == context's warehouse. Load warehouse from kanban's location (from ScanKanbanContext) and compare.
   - Actually, simpler: use the kanban's current warehouse context. The context already has target_warehouse_id and location_warehouse_id from the old query. Now that we'll switch the SQL to use kb.location_id, this changes. For now, keep the warehouse validation logic as-is.
4. createMovement() — pass plannedLocationId = context.getTargetLocationId() (the original line target), and actualLocationId as storageLocationId
5. upsertBalance() — use actualLocationId
6. Update kanban: `board.setLocationId(actualLocationId)` if changed; `board.setStatus(RECEIVED)`
7. Everything else same

### C3: createMovement() — add planned_location_id
Add before `movement.setWarehouseId()`:
```java
movement.setPlannedLocationId(context.getTargetLocationId());
```

### C4: upsertBalance() — accept explicit locationId parameter
Change signature to accept `(ScanKanbanContext context, Long actualLocationId)` and use actualLocationId instead of `context.getTargetLocationId()`.

## Subtask D: ScanInboundRequest — add optional locationId

File: `backend/src/main/java/com/scut/wms/inventory/ScanInboundRequest.java`

```java
public record ScanInboundRequest(
        @NotBlank(message = "看板码不能为空")
        String kanbanCode,
        Long locationId
) {}
```

## Subtask E: InboundOrderResponse — update LineDisplay + add kanban fields

File: `backend/src/main/java/com/scut/wms/inbound/InboundOrderResponse.java`

### E1: Add to LineDisplay
Add `Long containerTypeId` as a new parameter (last in constructor).

### E2: Add to main response (optional, for release)
No changes needed to the main response for now — release response fields added in Layer 2.

## Subtask F: ScanInboundResponse — add planned/actual location

File: `backend/src/main/java/com/scut/wms/inventory/ScanInboundResponse.java`

Add `Long plannedLocationId`, `String plannedLocationName`, `Long actualLocationId`, `String actualLocationName` to the record. Update scanInbound() to populate them.

## Subtask G: ScanKanbanContext — add fields for location info

File: `backend/src/main/java/com/scut/wms/inventory/ScanKanbanContext.java`

This is a query result type. Add these fields (with getters/setters):
- `Long plannedLocationId` (the original line.target_location_id)
- `Long containerTypeId`

These need to be populated by the Mapper XML SELECT queries.

## Subtask H: Mapper XML — 8 queries switch JOIN from iol.target_location_id to kb.location_id

### H1: InventoryMapper.xml

**selectScanKanbanForUpdate** + **selectKanbanContext**: Change JOIN:
```sql
-- OLD:
JOIN storage_location sl ON sl.id = l.target_location_id
-- NEW:
JOIN storage_location sl ON sl.id = kb.location_id
```
Add these to SELECT:
```sql
l.target_location_id AS planned_location_id,
kb.container_type_id
```

**selectKanbanTrace**: Change:
```sql
-- OLD:
JOIN storage_location sl ON sl.id = iol.target_location_id
-- NEW:
JOIN storage_location sl ON sl.id = kb.location_id
```

**selectFifoCandidateForUpdate**: Change:
```sql
-- OLD:
iol.target_warehouse_id AS warehouseId,
iol.target_location_id AS storageLocationId
-- NEW:
sl.warehouse_id AS warehouseId,
sl.id AS storageLocationId
```
And JOIN: `JOIN storage_location sl ON sl.id = kb.location_id` (add this JOIN if not present, or change existing)

**selectFifoRecommendations**: Change:
```sql
-- OLD:
JOIN storage_location sl ON sl.id = iol.target_location_id
JOIN warehouse w ON w.id = iol.target_warehouse_id
-- NEW:
JOIN storage_location sl ON sl.id = kb.location_id
JOIN warehouse w ON w.id = sl.warehouse_id
```

**selectInventoryMovements**: Add LEFT JOIN for planned_location:
```sql
LEFT JOIN storage_location pl ON pl.id = im.planned_location_id
```
And SELECT: `pl.location_code AS planned_location_code, pl.location_name AS planned_location_name`

### H2: InboundMapper.xml

**selectKanbanPrints** + **selectKanbanPrintsByFilter**: Change:
```sql
-- OLD:
JOIN storage_location sl ON sl.id = l.target_location_id
-- NEW:
JOIN storage_location sl ON sl.id = kb.location_id
```
Also add to SELECT: `ct.container_name AS container_type_name` from `JOIN container_type ct ON ct.id = kb.container_type_id`.

## Subtask I: KanbanPrintResponse — add fields

File: `backend/src/main/java/com/scut/wms/inbound/KanbanPrintResponse.java`

Add `containerTypeName` field (String). The Mapper XML SELECT already maps this.

## Subtask J: InventoryMovementView — add planned location fields

File: `backend/src/main/java/com/scut/wms/inventory/InventoryMovementView.java`

Add `plannedLocationCode` and `plannedLocationName` String fields with getters/setters.

## Subtask K: InventoryOverviewService — RECEIVED + LOCKED kanbans

File: `backend/src/main/java/com/scut/wms/inventory/InventoryOverviewService.java`

Find the method that builds warehouse overviews. The query that counts used boxes should count:
```sql
COUNT(*) FROM kanban_board WHERE location_id = X AND status IN ('RECEIVED', 'LOCKED')
```

If this service uses a Mapper, update the Mapper. If it uses direct queries, update the query. READ THE FILE FIRST to understand current implementation.

## Subtask L: LockMapper — selectLockDetails fix

Check file: `backend/src/main/resources/mapper/OutboundMapper.xml` (or wherever selectLockDetails lives — search for it first). Change:
```sql
sl.id = iol.target_location_id → sl.id = kb.location_id
```

## Verification

After all changes: `cd backend && mvn test`
Target: ALL 35 tests pass, BUILD SUCCESS.

Some test data may need updating (test factory methods that create InboundOrderRequest.Line without containerTypeId). If tests fail, UPDATE THE TEST DATA — do not remove the @NotNull annotation. Add `containerTypeId` field to test data with value matching an existing test container_type record.

## What NOT to change
- Do NOT change frontend code
- Do NOT change schema.sql (Layer 0 already done)
- Do NOT change DatabaseMigration.java
- Do NOT change OutboundOrderService.java (unless needed for test compilation)
- Do NOT change tests that are unrelated to the changed APIs

## Commit
```
feat: Layer 1 — multi-kanban generation, scan-inbound location override, mapper XML refactor

- Multi-kanban generation: plannedQty / capacityQty → N kanbans per line
- InboundOrderRequest.Line: add containerTypeId
- Scan-inbound: optional locationId for forced inbound + planned_location_id audit
- 8 Mapper XML queries: switch storage_location JOIN from iol.target_location_id to kb.location_id
- FIFO queries: use kanban's own location_id via storage_location JOIN
- InventoryOverview: count RECEIVED + LOCKED kanbans for location capacity
- KanbanPrint: add containerTypeName from kanban's container_type_id
```
