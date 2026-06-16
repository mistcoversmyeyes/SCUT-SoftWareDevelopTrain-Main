# Task 1: Layer 0 — Data Model DDL Implementation

**Global constraints (D24)**: All new fields use strict constraints (NOT NULL where applicable). Dev environment — full data rebuild, no historical compatibility needed. Service layer code MUST NOT write NULL fallback logic for new fields.

**Key decisions**: D02 (material:container=1:N via middle table), D05 (storage_location.max_capacity=box count), D13 (inventory_movement.planned_location_id for audit trail).

## Subtask 1: Create MaterialContainerType entity

New entity class at `backend/src/main/java/com/scut/wms/masterdata/MaterialContainerType.java`:

```java
@TableName("material_container_type")
public class MaterialContainerType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long materialId;
    private Long containerTypeId;
    private Integer isDefault;  // 0 or 1
    private LocalDateTime createdAt;
    // standard getters/setters
}
```

## Subtask 2: Modify Material.java — remove containerTypeId

File: `backend/src/main/java/com/scut/wms/masterdata/Material.java`
- Remove the `containerTypeId` field and its getter/setter
- Keep all other fields unchanged

## Subtask 3: Modify MaterialRequest.java

File: `backend/src/main/java/com/scut/wms/masterdata/MaterialRequest.java`
- Remove `Long containerTypeId` from the record definition

## Subtask 4: Modify KanbanBoard.java — add 2 fields

File: `backend/src/main/java/com/scut/wms/inbound/KanbanBoard.java`
- Add `private Long locationId;` + getter/setter
- Add `private Long containerTypeId;` + getter/setter

## Subtask 5: Modify InboundOrderLine.java — add containerTypeId

File: `backend/src/main/java/com/scut/wms/inbound/InboundOrderLine.java`
- Add `private Long containerTypeId;` + getter/setter

## Subtask 6: Modify InventoryMovement.java — add plannedLocationId

File: `backend/src/main/java/com/scut/wms/inventory/InventoryMovement.java`
- Add `private Long plannedLocationId;` + getter/setter

## Subtask 7: Update DatabaseMigration.java

File: `backend/src/main/java/com/scut/wms/config/DatabaseMigration.java`

Add these migration steps at the end of the existing `run()` method (before the catch block). Order matters:

```java
// --- WMS Refactor: Layer 0 DDL ---

// 1. Create material_container_type table (idempotent)
if (!tableExists(conn, "material_container_type")) {
    try (Statement stmt = conn.createStatement()) {
        stmt.execute("""
            CREATE TABLE material_container_type (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              material_id BIGINT NOT NULL,
              container_type_id BIGINT NOT NULL,
              is_default TINYINT DEFAULT 0,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              CONSTRAINT uk_mct UNIQUE (material_id, container_type_id),
              CONSTRAINT fk_mct_material FOREIGN KEY (material_id) REFERENCES material(id),
              CONSTRAINT fk_mct_container FOREIGN KEY (container_type_id) REFERENCES container_type(id),
              INDEX idx_mct_material (material_id),
              INDEX idx_mct_container (container_type_id)
            )
            """);
        log.info("迁移: 创建 material_container_type 表");
    }
}

// 2. Remove old material.container_type_id
dropForeignKeysForColumn(conn, "material", "container_type_id");
dropColumnIfExists(conn, "material", "container_type_id");

// 3. kanban_board add location_id + container_type_id (NOT NULL, D24)
ensureColumn(conn, "kanban_board", "location_id", "BIGINT NOT NULL DEFAULT 0");
ensureForeignKey(conn, "kanban_board", "location_id", "storage_location", "id", "fk_kanban_location");
ensureColumn(conn, "kanban_board", "container_type_id", "BIGINT NOT NULL DEFAULT 0");
ensureForeignKey(conn, "kanban_board", "container_type_id", "container_type", "id", "fk_kanban_container");

// 4. inbound_order_line add container_type_id (NOT NULL, D24)
ensureColumn(conn, "inbound_order_line", "container_type_id", "BIGINT NOT NULL DEFAULT 0");
ensureForeignKey(conn, "inbound_order_line", "container_type_id", "container_type", "id", "fk_inbound_line_container");

// 5. inventory_movement add planned_location_id (nullable — outbound movements have no planned location)
ensureColumn(conn, "inventory_movement", "planned_location_id", "BIGINT DEFAULT NULL");
ensureForeignKey(conn, "inventory_movement", "planned_location_id", "storage_location", "id", "fk_movement_planned_location");
```

You must also add a `tableExists` helper method to DatabaseMigration:

```java
private boolean tableExists(Connection conn, String tableName) throws Exception {
    DatabaseMetaData meta = conn.getMetaData();
    try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
        return rs.next();
    }
}
```

## Subtask 8: Update schema.sql

File: `backend/src/main/resources/schema.sql`

Changes:
1. Add `DROP TABLE IF EXISTS material_container_type;` to the DROP section (after `DROP TABLE IF EXISTS container_type;`)
2. Remove `container_type_id BIGINT,` line from the `material` CREATE TABLE
3. Add `container_type_id BIGINT NOT NULL DEFAULT 0,` to the `inbound_order_line` CREATE TABLE (after `target_location_id BIGINT NOT NULL,`)
4. Add `location_id BIGINT NOT NULL DEFAULT 0,` to the `kanban_board` CREATE TABLE (after `inbound_order_line_id`)
5. Add `container_type_id BIGINT NOT NULL DEFAULT 0,` to the `kanban_board` CREATE TABLE (after `location_id`)
6. Add FK constraints in kanban_board: `CONSTRAINT fk_kanban_location FOREIGN KEY (location_id) REFERENCES storage_location(id),` and `CONSTRAINT fk_kanban_container FOREIGN KEY (container_type_id) REFERENCES container_type(id),`
7. Add `INDEX idx_kanban_location_status (location_id, status)` after existing kanban_board indexes
8. Add `planned_location_id BIGINT DEFAULT NULL,` to the `inventory_movement` CREATE TABLE (after `storage_location_id BIGINT NOT NULL,`)
9. Add FK: `CONSTRAINT fk_movement_planned_location FOREIGN KEY (planned_location_id) REFERENCES storage_location(id),` to inventory_movement
10. Add the `material_container_type` CREATE TABLE statement (after `container_type` CREATE TABLE)
11. For `material` table: remove the `container_type_id` FK if there was one (there isn't in current schema.sql — material.container_type_id has no FK constraint in the DDL, only the column)

IMPORTANT — storage_location table: the current schema.sql does NOT have a `max_capacity` column at all. The DatabaseMigration may add it at runtime. Do NOT add `max_capacity` to schema.sql unless you find it already exists in the running DB. Just leave it as-is for this task.

## What NOT to touch in this task
- Do NOT change any Mapper XML files (SQL queries) — those are Layer 1/2
- Do NOT change any Service classes
- Do NOT change any Controller classes
- Do NOT change any Request/Response DTOs except MaterialRequest
- Do NOT change the frontend
- Do NOT change `MasterDataOptionsResponse.java` or `OptionItem.java` yet — these reference containerTypeId but will be updated in Layer 2

## Verification
After all changes, run: `cd backend && mvn test -q`
All 35 tests must pass: `BUILD SUCCESS, Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`

## Commit
Commit all changes with message:
```
feat: Layer 0 — data model DDL for kanban board, container types, and planned location

- Add material_container_type middle table (D02: material:container = 1:N)
- Remove material.container_type_id column
- Add kanban_board.location_id and kanban_board.container_type_id (NOT NULL)
- Add inbound_order_line.container_type_id (NOT NULL)
- Add inventory_movement.planned_location_id (nullable)
- Update schema.sql to match
```
