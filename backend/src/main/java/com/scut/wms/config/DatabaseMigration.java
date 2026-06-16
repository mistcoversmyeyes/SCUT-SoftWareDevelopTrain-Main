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
}
