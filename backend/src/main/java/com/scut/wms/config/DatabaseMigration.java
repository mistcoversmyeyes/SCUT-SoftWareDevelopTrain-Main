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

            ensureColumn(conn, "kanban_board", "locked_by_order_id",
                    "BIGINT DEFAULT NULL");
            ensureColumn(conn, "kanban_board", "locked_by_order_line_id",
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

            // 3. kanban_board add location_id + container_type_id (NOT NULL, D24)
            ensureColumn(conn, "kanban_board", "location_id", "BIGINT NOT NULL DEFAULT 0");
            try {
                ensureForeignKey(conn, "kanban_board", "location_id", "storage_location", "id", "fk_kanban_location");
            } catch (Exception e) {
                log.warn("添加外键 fk_kanban_location 失败（可能因存在无效引用值，重建数据后自动修复）: {}", e.getMessage());
            }
            ensureColumn(conn, "kanban_board", "container_type_id", "BIGINT NOT NULL DEFAULT 0");
            try {
                ensureForeignKey(conn, "kanban_board", "container_type_id", "container_type", "id", "fk_kanban_container");
            } catch (Exception e) {
                log.warn("添加外键 fk_kanban_container 失败（可能因存在无效引用值）: {}", e.getMessage());
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
        } catch (Exception e) {
            log.warn("数据库列迁移失败（不影响已有功能）: {}", e.getMessage());
        }
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
        String findFk = """
                SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = (SELECT DATABASE())
                  AND TABLE_NAME = '%s'
                  AND COLUMN_NAME = '%s'
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                """.formatted(table, column);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(findFk)) {
            while (rs.next()) {
                String fkName = rs.getString("CONSTRAINT_NAME");
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

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private boolean foreignKeyExists(Connection conn, String table, String fkName)
            throws Exception {
        String sql = """
                SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = (SELECT DATABASE())
                  AND TABLE_NAME = '%s'
                  AND CONSTRAINT_NAME = '%s'
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """.formatted(table, fkName);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
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
                  kanban_board_id BIGINT NOT NULL,
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
                  CONSTRAINT fk_lock_kanban FOREIGN KEY (kanban_board_id) REFERENCES kanban_board(id),
                  INDEX idx_lock_order (outbound_order_id),
                  INDEX idx_lock_kanban (kanban_board_id),
                  INDEX idx_lock_status (status)
                )
                """;
        log.info("迁移: CREATE TABLE inventory_lock");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

}
