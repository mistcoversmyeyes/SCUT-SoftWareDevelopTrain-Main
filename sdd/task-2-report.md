# Task 2 — Layer 1: Core Business Logic Implementation Report

## Subtask Checklist
- [x] A: InboundOrderRequest — add containerTypeId to Line record
- [x] B: InboundOrderService — insertLines + insertKanbans (multi-kanban generation) + toResponse LineDisplay update
- [x] C: InventoryService — scanInbound with location override, createMovement adds plannedLocationId, upsertBalance accepts explicit locationId
- [x] D: ScanInboundRequest — add optional locationId field
- [x] E: InboundOrderResponse — LineDisplay add containerTypeId
- [x] F: ScanInboundResponse — add planned/actual location fields
- [x] G: ScanKanbanContext — add plannedLocationId + containerTypeId
- [x] H: Mapper XML — 8 queries refactored (5 in InventoryMapper.xml, 2 in InboundMapper.xml)
- [x] I: KanbanPrintResponse — add containerTypeName
- [x] J: InventoryMovementView — add plannedLocationCode + plannedLocationName
- [ ] K: InventoryOverviewService — RECEIVED+LOCKED (files exist in parent repo but not in worktree branch; part of Layer 2)
- [ ] L: LockMapper selectLockDetails fix (files exist in parent repo but not in worktree branch; part of Layer 2)

## Test Results
```
mvn test (35/35)
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- AuthControllerTest
[INFO] Tests run: 18, Failures: 0, Errors: 0, Skipped: 0 -- InboundOrderControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- ScanInboundControllerTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- TraceQueryControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- MasterDataControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0 -- WmsApplicationTest
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Files Changed

### Service/Logic layer
- `backend/src/main/java/com/scut/wms/inbound/InboundOrderRequest.java` — Line record: added `@NotNull Long containerTypeId`
- `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java` — Added ContainerTypeMapper injection; insertLines passes containerTypeId; insertKanbans generates multi-kanban based on container capacity; toResponse adds containerTypeId to LineDisplay
- `backend/src/main/java/com/scut/wms/inventory/InventoryService.java` — Added StorageLocationMapper injection; scanInbound supports optional locationId for forced inbound; createMovement sets plannedLocationId; upsertBalance accepts explicit locationId parameter
- `backend/src/main/java/com/scut/wms/inventory/ScanInboundRequest.java` — Added optional `Long locationId` to record
- `backend/src/main/java/com/scut/wms/inbound/InboundOrderResponse.java` — LineDisplay: added `Long containerTypeId`
- `backend/src/main/java/com/scut/wms/inventory/ScanInboundResponse.java` — Added plannedLocationId, plannedLocationName, actualLocationId, actualLocationName
- `backend/src/main/java/com/scut/wms/inventory/ScanKanbanContext.java` — Added plannedLocationId + containerTypeId fields with getters/setters
- `backend/src/main/java/com/scut/wms/inbound/KanbanPrintResponse.java` — Added containerTypeName field
- `backend/src/main/java/com/scut/wms/inventory/InventoryMovementView.java` — Added plannedLocationCode + plannedLocationName fields with getters/setters

### Mapper XML
- `backend/src/main/resources/mapper/InventoryMapper.xml` — 5 queries refactored:
  - selectScanKanbanForUpdate: JOIN switched to kb.location_id; added planned_location_id and container_type_id to SELECT
  - selectKanbanContext: same changes
  - selectKanbanTrace: JOIN switched to kb.location_id
  - selectFifoCandidateForUpdate: SELECT + WHERE use sl.warehouse_id/sl.id via kb.location_id JOIN
  - selectFifoRecommendations: JOIN switched to kb.location_id, warehouse JOIN via sl.warehouse_id
  - selectInventoryMovements: added LEFT JOIN for planned_location; added planned_location_code/planned_location_name
- `backend/src/main/resources/mapper/InboundMapper.xml` — 2 queries refactored:
  - selectKanbanPrints: JOIN switched to kb.location_id; added container_type_name via container_type JOIN
  - selectKanbanPrintsByFilter: same changes

### Test data
- `backend/src/main/resources/data.sql` — Added location_id/container_type_id to kanban_board INSERT; added container_type_id to inbound_order_line INSERT; added UPDATE statements for existing records with default values
- `backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java` — Added containerTypeId to all test JSON requests; added @BeforeEach to fix existing board/line column values
- `backend/src/test/java/com/scut/wms/inventory/ScanInboundControllerTest.java` — resetBoard now sets location_id and container_type_id
- `backend/src/test/java/com/scut/wms/inventory/TraceQueryControllerTest.java` — resetBoard now sets location_id and container_type_id

## Concerns
1. **Subtask K (InventoryOverviewService)**: `InventoryOverviewService.java` (and related files) exist in the parent repo but were not present in the worktree branch. These are Layer 2 features that need to be completed separately.
2. **Subtask L (LockMapper selectLockDetails)**: `LockMapper.xml`, `InventoryLockMapper.java`, and related lock files exist in the parent repo but were not present in the worktree branch. These are Layer 2 features that need to be completed separately.
3. **Database FK constraints**: The DatabaseMigration attempts to add FK constraints on kanban_board.location_id and container_type_id, but fails because existing records have default values (0) that don't match the referenced tables. This is logged as a warning and does not block functionality. A future data cleanup migration should fix existing records.
4. **Test data fragility**: Tests now fix existing database records in `@BeforeEach` to ensure location_id/container_type_id have valid values. This is needed because `application-test.yml` sets `spring.sql.init.mode: never`, so data.sql changes don't apply to the test profile.
