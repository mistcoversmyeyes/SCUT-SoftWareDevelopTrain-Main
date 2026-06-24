package com.scut.wms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;

/**
 * 启动时自动补齐数据库缺失列（幂等，已存在的列跳过）。
 * 避免因 schema.sql 与实际库不一致导致运行时 SQL 错误。
 */
@Configuration
public class DatabaseMigration implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DatabaseMigration.class);

    private final DataSource dataSource;

    public DatabaseMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection()) {
            // ═══════════════════════════════════════════════
            // Step 0: Ensure ALL base tables exist (fresh DB)
            // ═══════════════════════════════════════════════
            ensureTables(conn);
            ensureAiImportTables(conn);

    // 确保 inventory_movement 出库关联列存在
            ensureColumn(conn, "inventory_movement", "outbound_order_id",
                    "BIGINT DEFAULT NULL");
            ensureColumn(conn, "inventory_movement", "outbound_order_line_id",
                    "BIGINT DEFAULT NULL");

            // 删除旧 source_warehouse_id / source_location_id 列（修复 500 错误）
            dropForeignKeysForColumn(conn, "outbound_order_line", "source_warehouse_id");
            dropForeignKeysForColumn(conn, "outbound_order_line", "source_location_id");
            dropColumnIfExists(conn, "outbound_order_line", "source_warehouse_id");
            dropColumnIfExists(conn, "outbound_order_line", "source_location_id");

            // 新增 supplier_id 列到出库单行
            ensureColumn(conn, "outbound_order_line", "supplier_id",
                    "BIGINT DEFAULT NULL");
            ensureForeignKey(conn, "outbound_order_line", "supplier_id",
                    "supplier", "id", "fk_outbound_line_supplier");

            // 让 outbound_order.supplier_id 可空（供应商改由行级管理）
            alterColumnNullable(conn, "outbound_order", "supplier_id",
                    "BIGINT DEFAULT NULL");

            // 入库单同样改为行级供应商
            ensureColumn(conn, "inbound_order_line", "supplier_id",
                    "BIGINT DEFAULT NULL");
            ensureForeignKey(conn, "inbound_order_line", "supplier_id",
                    "supplier", "id", "fk_inbound_line_supplier");
            alterColumnNullable(conn, "inbound_order", "supplier_id",
                    "BIGINT DEFAULT NULL");

            // 出库单行增加目标仓库/库位列（带单出库）
            ensureColumn(conn, "outbound_order_line", "target_warehouse_id",
                    "BIGINT DEFAULT NULL");
            ensureColumn(conn, "outbound_order_line", "target_location_id",
                    "BIGINT DEFAULT NULL");

            // 库位最大容量
            ensureColumn(conn, "storage_location", "max_capacity",
                    "DECIMAL(10,2) DEFAULT NULL");

            // ====== 锁货功能迁移 ======
            ensureLockTable(conn);
            ensureInventoryHoldTable(conn);

            ensureColumn(conn, "inventory_tag", "picked_qty",
                    "DECIMAL(18, 3) NOT NULL DEFAULT 0");
            ensureColumn(conn, "inventory_tag", "locked_by_order_id",
                    "BIGINT DEFAULT NULL");
            ensureColumn(conn, "inventory_tag", "locked_by_order_line_id",
                    "BIGINT DEFAULT NULL");

            ensureColumn(conn, "inventory_movement", "force_outbound",
                    "TINYINT(1) NOT NULL DEFAULT 0");
            ensureColumn(conn, "inventory_movement", "force_remark",
                    "VARCHAR(255) DEFAULT NULL");

            ensureColumn(conn, "outbound_order", "qrcode",
                    "VARCHAR(255) DEFAULT NULL");

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

            // 3. inventory_tag add location_id + container_type_id (NOT NULL, D24)
            ensureColumn(conn, "inventory_tag", "location_id", "BIGINT NOT NULL DEFAULT 0");
            try {
                ensureForeignKey(conn, "inventory_tag", "location_id", "storage_location", "id", "fk_inventory_tag_location");
            } catch (Exception e) {
                log.warn("添加外键 fk_inventory_tag_location 失败（可能因存在无效引用值，重建数据后自动修复）: {}", e.getMessage());
            }
            ensureColumn(conn, "inventory_tag", "container_type_id", "BIGINT NOT NULL DEFAULT 0");
            try {
                ensureForeignKey(conn, "inventory_tag", "container_type_id", "container_type", "id", "fk_inventory_tag_container");
            } catch (Exception e) {
                log.warn("添加外键 fk_inventory_tag_container 失败（可能因存在无效引用值）: {}", e.getMessage());
            }

            // 4. inbound_order_line add container_type_id (NOT NULL, D24)
            ensureColumn(conn, "inbound_order_line", "container_type_id", "BIGINT NOT NULL DEFAULT 0");
            try {
                ensureForeignKey(conn, "inbound_order_line", "container_type_id", "container_type", "id", "fk_inbound_line_container");
            } catch (Exception e) {
                log.warn("添加外键 fk_inbound_line_container 失败: {}", e.getMessage());
            }

            // 5. inventory_movement add planned_location_id (nullable)
            ensureColumn(conn, "inventory_movement", "planned_location_id", "BIGINT DEFAULT NULL");
            try {
                ensureForeignKey(conn, "inventory_movement", "planned_location_id", "storage_location", "id", "fk_movement_planned_location");
            } catch (Exception e) {
                log.warn("添加外键 fk_movement_planned_location 失败: {}", e.getMessage());
            }

            migrateKanbanBoardToInventoryTag(conn);
            migrateKanbanReferencesToInventoryTag(conn);
        } catch (Exception e) {
            log.warn("数据库列迁移失败（不影响已有功能）: {}", e.getMessage());
        }
    }

    private void migrateKanbanBoardToInventoryTag(Connection conn) throws Exception {
        if (!tableExists(conn, "kanban_board")) {
            return;
        }

        String idCol = "id";
        String codeCol = resolveColumn(conn, "kanban_board",
                "kanban_board_code", "board_code", "inventory_tag_code", "code");
        String inboundOrderIdCol = resolveColumn(conn, "kanban_board", "inbound_order_id");
        String inboundOrderLineIdCol = resolveColumn(conn, "kanban_board", "inbound_order_line_id");
        String qtyCol = resolveColumn(conn, "kanban_board", "board_qty", "qty");
        String statusCol = resolveColumn(conn, "kanban_board", "status");
        String printedAtCol = resolveColumn(conn, "kanban_board", "printed_at", "print_at", "printed_time");
        String receivedAtCol = resolveColumn(conn, "kanban_board", "received_at", "inbound_at", "receive_at");
        String locationIdCol = resolveColumn(conn, "kanban_board", "location_id", "storage_location_id", "target_location_id");
        String containerTypeIdCol = resolveColumn(conn, "kanban_board", "container_type_id", "container_id");
        String lockedByOrderIdCol = resolveColumn(conn, "kanban_board", "locked_by_order_id", "locked_order_id", "hold_order_id");
        String lockedByOrderLineIdCol = resolveColumn(conn, "kanban_board", "locked_by_order_line_id", "locked_order_line_id", "hold_order_line_id");
        String createdAtCol = resolveColumn(conn, "kanban_board", "created_at");
        String updatedAtCol = resolveColumn(conn, "kanban_board", "updated_at");

        if (codeCol == null || inboundOrderIdCol == null || inboundOrderLineIdCol == null || qtyCol == null || statusCol == null) {
            log.warn("kanban_board 表缺少关键列，跳过迁移");
            return;
        }

        String codeExpr = "CASE WHEN kb." + codeCol + " LIKE 'KB:v1:%' THEN CONCAT('IT:v1:', SUBSTRING(kb." + codeCol + ", 7)) ELSE REPLACE(kb." + codeCol + ", 'KB:v1:', 'IT:v1:') END";
        String locationExpr = locationIdCol == null ? "0" : "COALESCE(kb." + locationIdCol + ", 0)";
        String containerExpr = containerTypeIdCol == null ? "0" : "COALESCE(kb." + containerTypeIdCol + ", 0)";
        String lockedByOrderIdExpr = lockedByOrderIdCol == null ? "NULL" : "kb." + lockedByOrderIdCol;
        String lockedByOrderLineIdExpr = lockedByOrderLineIdCol == null ? "NULL" : "kb." + lockedByOrderLineIdCol;
        String printedAtExpr = printedAtCol == null ? "NULL" : "kb." + printedAtCol;
        String receivedAtExpr = receivedAtCol == null ? "NULL" : "kb." + receivedAtCol;
        String createdAtExpr = createdAtCol == null ? "CURRENT_TIMESTAMP" : "kb." + createdAtCol;
        String updatedAtExpr = updatedAtCol == null ? "CURRENT_TIMESTAMP" : "kb." + updatedAtCol;

        String sql = "INSERT INTO inventory_tag (" +
                "id, " +
                "inventory_tag_code, " +
                "inbound_order_id, " +
                "inbound_order_line_id, " +
                "board_qty, " +
                "picked_qty, " +
                "status, " +
                "printed_at, " +
                "received_at, " +
                "location_id, " +
                "container_type_id, " +
                "locked_by_order_id, " +
                "locked_by_order_line_id, " +
                "created_at, " +
                "updated_at" +
                ") SELECT " +
                "kb." + idCol + ", " +
                codeExpr + ", " +
                "kb." + inboundOrderIdCol + ", " +
                "kb." + inboundOrderLineIdCol + ", " +
                "kb." + qtyCol + ", " +
                "0, " +
                "kb." + statusCol + ", " +
                printedAtExpr + ", " +
                receivedAtExpr + ", " +
                locationExpr + ", " +
                containerExpr + ", " +
                lockedByOrderIdExpr + ", " +
                lockedByOrderLineIdExpr + ", " +
                createdAtExpr + ", " +
                updatedAtExpr +
                " FROM kanban_board kb " +
                "WHERE NOT EXISTS (SELECT 1 FROM inventory_tag it WHERE it.id = kb." + idCol + ")";
        log.info("迁移: 从 kanban_board 插入 inventory_tag");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void migrateKanbanReferencesToInventoryTag(Connection conn) throws Exception {
        migrateKanbanForeignKeyColumn(conn, "inventory_hold", "kanban_board_id", "inventory_tag_id");
        migrateKanbanForeignKeyColumn(conn, "inventory_lock", "kanban_board_id", "inventory_tag_id");
        migrateKanbanForeignKeyColumn(conn, "inventory_movement", "kanban_board_id", "inventory_tag_id");

        dropTableIfExists(conn, "kanban_board");
    }

    private void migrateKanbanForeignKeyColumn(Connection conn, String table, String oldColumn, String newColumn) throws Exception {
        if (!tableExists(conn, table) || !columnExists(conn, table, oldColumn)) {
            return;
        }

        ensureColumn(conn, table, newColumn, "BIGINT DEFAULT NULL");

        String sql = "UPDATE " + table + " SET " + newColumn + " = " + oldColumn +
                " WHERE " + newColumn + " IS NULL AND " + oldColumn + " IS NOT NULL";
        log.info("迁移: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }

        dropForeignKeysForColumn(conn, table, oldColumn);
        dropColumnIfExists(conn, table, oldColumn);
    }

    private void dropTableIfExists(Connection conn, String table) throws Exception {
        if (!tableExists(conn, table)) {
            return;
        }
        String sql = "DROP TABLE " + table;
        log.info("迁移: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private String resolveColumn(Connection conn, String table, String... candidates) throws Exception {
        for (String candidate : candidates) {
            if (columnExists(conn, table, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private void ensureColumn(Connection conn, String table, String column, String definition)
            throws Exception {
        if (columnExists(conn, table, column)) {
            return;
        }
        String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        log.info("迁移: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void dropColumnIfExists(Connection conn, String table, String column)
            throws Exception {
        if (!columnExists(conn, table, column)) {
            return;
        }
        String sql = "ALTER TABLE " + table + " DROP COLUMN " + column;
        log.info("迁移: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void dropForeignKeysForColumn(Connection conn, String table, String column)
            throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getImportedKeys(conn.getCatalog(), null, table)) {
            while (rs.next()) {
                if (!Objects.equals(column, rs.getString("FKCOLUMN_NAME"))) {
                    continue;
                }
                String fkName = rs.getString("FK_NAME");
                String sql = "ALTER TABLE " + table + " DROP FOREIGN KEY " + fkName;
                log.info("迁移: {}", sql);
                try (Statement dropStmt = conn.createStatement()) {
                    dropStmt.execute(sql);
                }
            }
        }
    }

    private void ensureForeignKey(Connection conn, String table, String column,
                                   String refTable, String refColumn, String fkName)
            throws Exception {
        if (foreignKeyExists(conn, table, fkName)) {
            return;
        }
        String sql = "ALTER TABLE " + table + " ADD CONSTRAINT " + fkName
                + " FOREIGN KEY (" + column + ") REFERENCES " + refTable + "(" + refColumn + ")";
        log.info("迁移: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void alterColumnNullable(Connection conn, String table, String column,
                                      String definition) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            if (rs.next()) {
                int nullable = rs.getInt("NULLABLE");
                if (nullable == DatabaseMetaData.columnNullable) {
                    return;
                }
            }
        }
        String sql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition;
        log.info("迁移: {}", sql);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private void ensureTables(Connection conn) throws Exception {
        ensureTable(conn, "supplier", """
            CREATE TABLE supplier (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              supplier_code VARCHAR(64) NOT NULL UNIQUE,
              supplier_name VARCHAR(128) NOT NULL,
              contact_name VARCHAR(64),
              contact_phone VARCHAR(32),
              status VARCHAR(32) NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """);

        ensureTable(conn, "container_type", """
            CREATE TABLE container_type (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              container_code VARCHAR(64) NOT NULL UNIQUE,
              container_name VARCHAR(128) NOT NULL,
              capacity_qty DECIMAL(18, 3),
              status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """);

        ensureTable(conn, "material", """
            CREATE TABLE material (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              material_code VARCHAR(64) NOT NULL UNIQUE,
              material_name VARCHAR(128) NOT NULL,
              specification VARCHAR(128),
              unit VARCHAR(32) NOT NULL,
              supplier_id BIGINT,
              low_stock_qty DECIMAL(18, 3),
              high_stock_qty DECIMAL(18, 3),
              status VARCHAR(32) NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT fk_material_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
            )
            """);

        ensureTable(conn, "warehouse", """
            CREATE TABLE warehouse (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              warehouse_code VARCHAR(64) NOT NULL UNIQUE,
              warehouse_name VARCHAR(128) NOT NULL,
              status VARCHAR(32) NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
            """);

        ensureTable(conn, "storage_location", """
            CREATE TABLE storage_location (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              warehouse_id BIGINT NOT NULL,
              location_code VARCHAR(64) NOT NULL,
              location_name VARCHAR(128) NOT NULL,
              max_capacity DECIMAL(10,2) DEFAULT NULL,
              status VARCHAR(32) NOT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT uk_storage_location UNIQUE (warehouse_id, location_code),
              CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
            )
            """);

        ensureTable(conn, "inbound_order", """
            CREATE TABLE inbound_order (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              inbound_no VARCHAR(64) NOT NULL UNIQUE,
              supplier_id BIGINT DEFAULT NULL,
              source_doc_no VARCHAR(64),
              status VARCHAR(32) NOT NULL,
              remark VARCHAR(255),
              released_at DATETIME,
              completed_at DATETIME,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              INDEX idx_inbound_order_status (status),
              INDEX idx_inbound_order_supplier_status (supplier_id, status)
            )
            """);

        ensureTable(conn, "inbound_order_line", """
            CREATE TABLE inbound_order_line (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              inbound_order_id BIGINT NOT NULL,
              line_no INT NOT NULL,
              material_id BIGINT NOT NULL,
              supplier_id BIGINT DEFAULT NULL,
              planned_qty DECIMAL(18, 3) NOT NULL,
              received_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
              target_warehouse_id BIGINT NOT NULL,
              target_location_id BIGINT NOT NULL,
              container_type_id BIGINT NOT NULL DEFAULT 0,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT uk_inbound_order_line UNIQUE (inbound_order_id, line_no),
              CONSTRAINT fk_inbound_line_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_order(id),
              CONSTRAINT fk_inbound_line_material FOREIGN KEY (material_id) REFERENCES material(id),
              CONSTRAINT fk_inbound_line_warehouse FOREIGN KEY (target_warehouse_id) REFERENCES warehouse(id),
              CONSTRAINT fk_inbound_line_location FOREIGN KEY (target_location_id) REFERENCES storage_location(id)
            )
            """);

        ensureTable(conn, "outbound_order", """
            CREATE TABLE outbound_order (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              outbound_no VARCHAR(64) NOT NULL UNIQUE,
              supplier_id BIGINT DEFAULT NULL,
              purpose VARCHAR(64),
              source_doc_no VARCHAR(64),
              status VARCHAR(32) NOT NULL,
              remark VARCHAR(255),
              released_at DATETIME,
              completed_at DATETIME,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              INDEX idx_outbound_order_status (status)
            )
            """);

        ensureTable(conn, "outbound_order_line", """
            CREATE TABLE outbound_order_line (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              outbound_order_id BIGINT NOT NULL,
              line_no INT NOT NULL,
              material_id BIGINT NOT NULL,
              supplier_id BIGINT DEFAULT NULL,
              planned_qty DECIMAL(18, 3) NOT NULL,
              picked_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT uk_outbound_order_line UNIQUE (outbound_order_id, line_no),
              CONSTRAINT fk_outbound_line_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_order(id),
              CONSTRAINT fk_outbound_line_material FOREIGN KEY (material_id) REFERENCES material(id)
            )
            """);

        ensureTable(conn, "inventory_tag", """
            CREATE TABLE inventory_tag (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              inventory_tag_code VARCHAR(128) NOT NULL UNIQUE,
              inbound_order_id BIGINT NOT NULL,
              inbound_order_line_id BIGINT NOT NULL,
              location_id BIGINT NOT NULL DEFAULT 0,
              container_type_id BIGINT NOT NULL DEFAULT 0,
              board_qty DECIMAL(18, 3) NOT NULL,
              picked_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
              status VARCHAR(32) NOT NULL,
              printed_at DATETIME,
              received_at DATETIME,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT fk_inventory_tag_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_order(id),
              CONSTRAINT fk_inventory_tag_line FOREIGN KEY (inbound_order_line_id) REFERENCES inbound_order_line(id),
              INDEX idx_inventory_tag_line_status (inbound_order_line_id, status),
              INDEX idx_inventory_tag_location_status (location_id, status)
            )
            """);

        ensureTable(conn, "inventory_movement", """
            CREATE TABLE inventory_movement (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              movement_no VARCHAR(64) NOT NULL UNIQUE,
              movement_type VARCHAR(32) NOT NULL,
              source_type VARCHAR(32) NOT NULL,
              source_id BIGINT,
              inventory_tag_id BIGINT,
              material_id BIGINT NOT NULL,
              warehouse_id BIGINT NOT NULL,
              storage_location_id BIGINT NOT NULL,
              qty DECIMAL(18, 3) NOT NULL,
              occurred_at DATETIME NOT NULL,
              operator_name VARCHAR(64),
              outbound_order_id BIGINT DEFAULT NULL,
              outbound_order_line_id BIGINT DEFAULT NULL,
              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_movement_material_time (material_id, occurred_at),
              INDEX idx_movement_location_time (warehouse_id, storage_location_id, occurred_at)
            )
            """);

        ensureTable(conn, "inventory_balance", """
            CREATE TABLE inventory_balance (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              material_id BIGINT NOT NULL,
              warehouse_id BIGINT NOT NULL,
              storage_location_id BIGINT NOT NULL,
              on_hand_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              CONSTRAINT uk_inventory_balance UNIQUE (material_id, warehouse_id, storage_location_id),
              CONSTRAINT fk_balance_material FOREIGN KEY (material_id) REFERENCES material(id),
              CONSTRAINT fk_balance_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
              CONSTRAINT fk_balance_location FOREIGN KEY (storage_location_id) REFERENCES storage_location(id)
            )
            """);
    }

    private void ensureAiImportTables(Connection conn) throws Exception {
        ensureTable(conn, "ai_import_batch", """
            CREATE TABLE ai_import_batch (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              import_type VARCHAR(64) NOT NULL,
              file_name VARCHAR(255) NOT NULL,
              template_version VARCHAR(64) NOT NULL,
              total_rows INT NOT NULL DEFAULT 0,
              success_rows INT NOT NULL DEFAULT 0,
              failed_rows INT NOT NULL DEFAULT 0,
              imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_ai_import_type_time (import_type, imported_at)
            )
            """);

        ensureTable(conn, "ai_inventory_flow_history", """
            CREATE TABLE ai_inventory_flow_history (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              batch_id BIGINT NOT NULL,
              import_row_no INT NOT NULL,
              business_date DATE NOT NULL,
              material_code VARCHAR(64) NOT NULL,
              warehouse_code VARCHAR(64) NOT NULL,
              location_code VARCHAR(64) NOT NULL,
              board_code VARCHAR(128) NOT NULL,
              movement_type VARCHAR(32) NOT NULL,
              quantity DECIMAL(18, 3) NOT NULL,
              source_order_no VARCHAR(64) NOT NULL,
              quality_status VARCHAR(32),
              imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
              CONSTRAINT fk_ai_flow_batch FOREIGN KEY (batch_id) REFERENCES ai_import_batch(id),
              INDEX idx_ai_flow_batch_row (batch_id, import_row_no),
              INDEX idx_ai_flow_material_date (material_code, business_date),
              INDEX idx_ai_flow_movement_date (movement_type, business_date)
            )
            """);
    }
    private void ensureTable(Connection conn, String tableName, String createSql) throws Exception {
        if (tableExists(conn, tableName)) return;
        log.info("迁移: 创建表 {}", tableName);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private boolean foreignKeyExists(Connection conn, String table, String fkName)
            throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getImportedKeys(conn.getCatalog(), null, table)) {
            while (rs.next()) {
                if (Objects.equals(fkName, rs.getString("FK_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void ensureLockTable(Connection conn) throws Exception {
        if (tableExists(conn, "inventory_lock")) {
            return;
        }
        String sql = """
                CREATE TABLE inventory_lock (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  outbound_order_id BIGINT NOT NULL,
                  outbound_order_line_id BIGINT NOT NULL,
                  inventory_tag_id BIGINT NOT NULL,
                  material_id BIGINT NOT NULL,
                  lock_qty DECIMAL(18, 3) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'LOCKED',
                  stolen_by_order_id BIGINT DEFAULT NULL,
                  stolen_at DATETIME DEFAULT NULL,
                  unlocked_at DATETIME DEFAULT NULL,
                  unlocked_by VARCHAR(64) DEFAULT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  CONSTRAINT fk_lock_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_order(id),
                  CONSTRAINT fk_lock_line FOREIGN KEY (outbound_order_line_id) REFERENCES outbound_order_line(id),
                  CONSTRAINT fk_lock_inventory_tag FOREIGN KEY (inventory_tag_id) REFERENCES inventory_tag(id),
                  INDEX idx_lock_order (outbound_order_id),
                  INDEX idx_lock_inventory_tag (inventory_tag_id),
                  INDEX idx_lock_status (status)
                )
                """;
        log.info("迁移: CREATE TABLE inventory_lock");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private void ensureInventoryHoldTable(Connection conn) throws Exception {
        if (tableExists(conn, "inventory_hold")) {
            return;
        }
        String sql = """
                CREATE TABLE inventory_hold (
                  id BIGINT PRIMARY KEY AUTO_INCREMENT,
                  inventory_tag_id BIGINT NOT NULL,
                  hold_type VARCHAR(32) NOT NULL,
                  hold_qty DECIMAL(18, 3) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  reason VARCHAR(128) NOT NULL,
                  remark VARCHAR(255),
                  operator_name VARCHAR(64) NOT NULL,
                  released_reason VARCHAR(128) DEFAULT NULL,
                  released_remark VARCHAR(255) DEFAULT NULL,
                  released_by VARCHAR(64) DEFAULT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  released_at DATETIME DEFAULT NULL,
                  CONSTRAINT fk_hold_inventory_tag FOREIGN KEY (inventory_tag_id) REFERENCES inventory_tag(id),
                  INDEX idx_hold_inventory_tag_status (inventory_tag_id, status),
                  INDEX idx_hold_type_status (hold_type, status)
                )
                """;
        log.info("迁移: CREATE TABLE inventory_hold");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

}
