# Task 1: Layer 0 — Data Model DDL Implementation Report

## Subtask Checklist

- [x] Subtask 1: Create MaterialContainerType entity
- [x] Subtask 2: Modify Material.java — remove containerTypeId
- [x] Subtask 3: Modify MaterialRequest.java — remove containerTypeId
- [x] Subtask 4: Modify KanbanBoard.java — add locationId + containerTypeId
- [x] Subtask 5: Modify InboundOrderLine.java — add containerTypeId
- [x] Subtask 6: Modify InventoryMovement.java — add plannedLocationId
- [x] Subtask 7: Update DatabaseMigration.java — migration steps + tableExists helper
- [x] Subtask 8: Update schema.sql — all DDL changes

## Test Command and Output

```
cd backend && mvn test -q
```

**Result: BUILD FAILURE — Compilation error**

```
[ERROR] COMPILATION ERROR :
[ERROR] MasterDataService.java:[102,44] cannot find symbol: method containerTypeId()
[ERROR] MasterDataService.java:[125,44] cannot find symbol: method containerTypeId()
[ERROR] MasterDataService.java:[235,20] cannot find symbol: method containerTypeId()
[ERROR] MasterDataService.java:[236,81] cannot find symbol: method containerTypeId()
```

Tests cannot run because `mvn compile` fails. There are 35 tests defined in the project; none could execute.

## List of ALL Files Changed

1. `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerType.java` — **NEW**
2. `backend/src/main/java/com/scut/wms/masterdata/Material.java` — removed `containerTypeId` field + getter/setter
3. `backend/src/main/java/com/scut/wms/masterdata/MaterialRequest.java` — removed `Long containerTypeId` from record
4. `backend/src/main/java/com/scut/wms/inbound/KanbanBoard.java` — added `locationId` + `containerTypeId` fields + getters/setters
5. `backend/src/main/java/com/scut/wms/inbound/InboundOrderLine.java` — added `containerTypeId` field + getter/setter
6. `backend/src/main/java/com/scut/wms/inventory/InventoryMovement.java` — added `plannedLocationId` field + getter/setter
7. `backend/src/main/java/com/scut/wms/config/DatabaseMigration.java` — added Layer 0 DDL migration steps + `tableExists()` helper
8. `backend/src/main/resources/schema.sql` — all DDL changes per brief

## Self-Review Findings

- All new getters and setters are present and follow existing patterns
- schema.sql is consistent with DatabaseMigration.java and entity annotations
- No Mapper XML, Service, or Controller files were modified
- Test files do not reference `containerTypeId`, `getContainerTypeId`, or `setContainerTypeId` (no test changes needed)
- `MasterDataOptionsResponse.java` has a `List<OptionItem> containerTypes` field — not a compile error currently; will need updating in Layer 2
- `OptionItem.java` does not reference `containerTypeId` — no impact

## Compile Errors / Concerns

The only compile errors are in `MasterDataService.java` (4 occurrences), which references the removed `Material.containerTypeId` and `MaterialRequest.containerTypeId()`:

| File | Line | Symbol | Reason |
|------|------|--------|--------|
| MasterDataService.java | 102 | `material.setContainerTypeId(request.containerTypeId())` | `setContainerTypeId` removed from Material; `containerTypeId()` removed from MaterialRequest |
| MasterDataService.java | 125 | `material.setContainerTypeId(request.containerTypeId())` | Same as above |
| MasterDataService.java | 235 | `request.containerTypeId() != null` | `containerTypeId()` removed from MaterialRequest |
| MasterDataService.java | 236 | `containerTypeMapper.selectById(request.containerTypeId())` | Same as above |

These will need to be updated in Layer 1 or Layer 2 (business logic / API layer). The new `material_container_type` middle table replaces the old `material.containerTypeId` direct field, so service logic that sets or validates `containerTypeId` on Material/MaterialRequest must be refactored to use the new `MaterialContainerType` entity.

No other compile errors were found in the codebase.
