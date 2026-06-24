package com.scut.wms.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseMigrationTest {
    @Test
    void addsPickedQtyWhenInventoryTagAlreadyExistsWithoutIt() throws Exception {
        DataSource dataSource = legacyDataSource();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE inventory_tag (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      inventory_tag_code VARCHAR(128) NOT NULL UNIQUE,
                      inbound_order_id BIGINT NOT NULL,
                      inbound_order_line_id BIGINT NOT NULL,
                      board_qty DECIMAL(18, 3) NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      printed_at DATETIME,
                      received_at DATETIME,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        new DatabaseMigration(dataSource).run();

        assertThat(columnExists(dataSource, "inventory_tag", "picked_qty")).isTrue();
    }

    @Test
    void migrateKanbanBoardToInventoryTagAndDropOldTable() throws Exception {
        DataSource dataSource = kanbanLegacyDataSource();
        new DatabaseMigration(dataSource).run();

        assertThat(tableExists(dataSource, "kanban_board")).isFalse();
        assertThat(columnExists(dataSource, "inventory_tag", "inventory_tag_code")).isTrue();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT id, inventory_tag_code, inbound_order_id, inbound_order_line_id, board_qty, status, printed_at, received_at, location_id, container_type_id, locked_by_order_id, locked_by_order_line_id FROM inventory_tag ORDER BY id");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(101L);
            assertThat(rs.getString("inventory_tag_code")).isEqualTo("IT:v1:KB001");
            assertThat(rs.getLong("inbound_order_id")).isEqualTo(1L);
            assertThat(rs.getLong("inbound_order_line_id")).isEqualTo(10L);
            assertThat(rs.getBigDecimal("board_qty")).isEqualByComparingTo("8.500");
            assertThat(rs.getString("status")).isEqualTo("RECEIVED");
            assertThat(rs.getTimestamp("printed_at")).isNotNull();
            assertThat(rs.getTimestamp("received_at")).isNotNull();
            assertThat(rs.getLong("location_id")).isEqualTo(11L);
            assertThat(rs.getLong("container_type_id")).isEqualTo(21L);
            assertThat(rs.getLong("locked_by_order_id")).isEqualTo(31L);
            assertThat(rs.getLong("locked_by_order_line_id")).isEqualTo(41L);
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    void migrateKanbanReferencesToInventoryTagIdAndDropOldColumns() throws Exception {
        DataSource dataSource = kanbanLegacyDataSource();
        new DatabaseMigration(dataSource).run();

        assertThat(columnExists(dataSource, "inventory_hold", "inventory_tag_id")).isTrue();
        assertThat(columnExists(dataSource, "inventory_lock", "inventory_tag_id")).isTrue();
        assertThat(columnExists(dataSource, "inventory_movement", "inventory_tag_id")).isTrue();
        assertThat(columnExists(dataSource, "inventory_hold", "kanban_board_id")).isFalse();
        assertThat(columnExists(dataSource, "inventory_lock", "kanban_board_id")).isFalse();
        assertThat(columnExists(dataSource, "inventory_movement", "kanban_board_id")).isFalse();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet holdRs = stmt.executeQuery("SELECT inventory_tag_id FROM inventory_hold ORDER BY id");
            assertThat(holdRs.next()).isTrue();
            assertThat(holdRs.getLong("inventory_tag_id")).isEqualTo(101L);
            assertThat(holdRs.next()).isFalse();

            ResultSet lockRs = stmt.executeQuery("SELECT inventory_tag_id FROM inventory_lock ORDER BY id");
            assertThat(lockRs.next()).isTrue();
            assertThat(lockRs.getLong("inventory_tag_id")).isEqualTo(101L);
            assertThat(lockRs.next()).isFalse();

            ResultSet moveRs = stmt.executeQuery("SELECT inventory_tag_id FROM inventory_movement ORDER BY id");
            assertThat(moveRs.next()).isTrue();
            assertThat(moveRs.getLong("inventory_tag_id")).isEqualTo(101L);
            assertThat(moveRs.next()).isFalse();
        }
    }

    @Test
    void isIdempotentWhenStartedTwice() throws Exception {
        DataSource dataSource = kanbanLegacyDataSource();
        new DatabaseMigration(dataSource).run();
        new DatabaseMigration(dataSource).run();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM inventory_tag");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("cnt")).isEqualTo(1L);

            ResultSet holdRs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM inventory_hold");
            assertThat(holdRs.next()).isTrue();
            assertThat(holdRs.getLong("cnt")).isEqualTo(1L);
        }
    }

    private DataSource legacyDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:migration_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private DataSource kanbanLegacyDataSource() throws Exception {
        DataSource dataSource = legacyDataSource();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE inbound_order (id BIGINT PRIMARY KEY, supplier_id BIGINT)");
            stmt.execute("CREATE TABLE inbound_order_line (id BIGINT PRIMARY KEY)");
            stmt.execute("CREATE TABLE storage_location (id BIGINT PRIMARY KEY)");
            stmt.execute("CREATE TABLE container_type (id BIGINT PRIMARY KEY)");
            stmt.execute("CREATE TABLE outbound_order (id BIGINT PRIMARY KEY, supplier_id BIGINT)");
            stmt.execute("CREATE TABLE outbound_order_line (id BIGINT PRIMARY KEY)");
            stmt.execute("CREATE TABLE material (id BIGINT PRIMARY KEY)");
            stmt.execute("CREATE TABLE warehouse (id BIGINT PRIMARY KEY)");
            stmt.execute("CREATE TABLE kanban_board (" +
                    "id BIGINT PRIMARY KEY, " +
                    "kanban_board_code VARCHAR(128) NOT NULL, " +
                    "inbound_order_id BIGINT NOT NULL, " +
                    "inbound_order_line_id BIGINT NOT NULL, " +
                    "board_qty DECIMAL(18, 3) NOT NULL, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "printed_at DATETIME, " +
                    "received_at DATETIME, " +
                    "location_id BIGINT NOT NULL, " +
                    "container_type_id BIGINT NOT NULL, " +
                    "locked_by_order_id BIGINT, " +
                    "locked_by_order_line_id BIGINT, " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE TABLE inventory_hold (" +
                    "id BIGINT PRIMARY KEY, " +
                    "kanban_board_id BIGINT NOT NULL, " +
                    "hold_type VARCHAR(32) NOT NULL, " +
                    "hold_qty DECIMAL(18, 3) NOT NULL, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "reason VARCHAR(128) NOT NULL, " +
                    "operator_name VARCHAR(64) NOT NULL" +
                    ")");
            stmt.execute("CREATE TABLE inventory_lock (" +
                    "id BIGINT PRIMARY KEY, " +
                    "outbound_order_id BIGINT NOT NULL, " +
                    "outbound_order_line_id BIGINT NOT NULL, " +
                    "kanban_board_id BIGINT NOT NULL, " +
                    "material_id BIGINT NOT NULL, " +
                    "lock_qty DECIMAL(18, 3) NOT NULL, " +
                    "status VARCHAR(32) NOT NULL, " +
                    "stolen_by_order_id BIGINT, " +
                    "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE TABLE inventory_movement (" +
                    "id BIGINT PRIMARY KEY, " +
                    "kanban_board_id BIGINT, " +
                    "movement_no VARCHAR(64), " +
                    "movement_type VARCHAR(32), " +
                    "source_type VARCHAR(32), " +
                    "source_id BIGINT, " +
                    "material_id BIGINT NOT NULL, " +
                    "warehouse_id BIGINT NOT NULL, " +
                    "storage_location_id BIGINT NOT NULL, " +
                    "qty DECIMAL(18, 3) NOT NULL, " +
                    "occurred_at DATETIME NOT NULL, " +
                    "operator_name VARCHAR(64)" +
                    ")");

            stmt.execute("INSERT INTO inbound_order (id) VALUES (1)");
            stmt.execute("INSERT INTO inbound_order_line (id) VALUES (10)");
            stmt.execute("INSERT INTO storage_location (id) VALUES (11)");
            stmt.execute("INSERT INTO container_type (id) VALUES (21)");
            stmt.execute("INSERT INTO outbound_order (id) VALUES (100)");
            stmt.execute("INSERT INTO outbound_order_line (id) VALUES (200)");
            stmt.execute("INSERT INTO material (id) VALUES (300)");
            stmt.execute("INSERT INTO warehouse (id) VALUES (400)");
            stmt.execute("INSERT INTO kanban_board (" +
                    "id, kanban_board_code, inbound_order_id, inbound_order_line_id, board_qty, status, printed_at, received_at, location_id, container_type_id, locked_by_order_id, locked_by_order_line_id" +
                    ") VALUES (" +
                    "101, 'KB:v1:KB001', 1, 10, 8.500, 'RECEIVED', " +
                    "TIMESTAMP '2026-06-20 10:00:00', TIMESTAMP '2026-06-21 09:00:00', 11, 21, 31, 41)");
            stmt.execute("INSERT INTO inventory_hold (id, kanban_board_id, hold_type, hold_qty, status, reason, operator_name) " +
                    "VALUES (1, 101, 'MANUAL_LOCK', 1.000, 'ACTIVE', 'for test', 'tester')");
            stmt.execute("INSERT INTO inventory_lock (id, outbound_order_id, outbound_order_line_id, kanban_board_id, material_id, lock_qty, status) " +
                    "VALUES (1, 100, 200, 101, 300, 1.000, 'LOCKED')");
            stmt.execute("INSERT INTO inventory_movement (id, kanban_board_id, movement_no, movement_type, source_type, source_id, material_id, warehouse_id, storage_location_id, qty, occurred_at, operator_name) " +
                    "VALUES (1, 101, 'M001', 'INBOUND', 'KANBAN', 900, 300, 400, 11, 8.500, TIMESTAMP '2026-06-20 10:30:00', 'system')");
        }

        return dataSource;
    }

    private boolean columnExists(DataSource dataSource, String table, String column) throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    private boolean tableExists(DataSource dataSource, String table) throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(null, null, table, null)) {
            return rs.next();
        }
    }
}
