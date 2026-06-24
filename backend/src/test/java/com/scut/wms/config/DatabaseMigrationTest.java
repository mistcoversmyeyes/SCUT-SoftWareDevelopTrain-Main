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
    void addsInventoryTagIdWhenInventoryHoldAlreadyExistsWithoutIt() throws Exception {
        DataSource dataSource = legacyDataSource();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE inventory_hold (
                      id BIGINT PRIMARY KEY AUTO_INCREMENT,
                      hold_type VARCHAR(32) NOT NULL,
                      status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                      reason VARCHAR(128) NOT NULL,
                      operator_name VARCHAR(64) NOT NULL,
                      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }

        new DatabaseMigration(dataSource).run();

        assertThat(columnExists(dataSource, "inventory_hold", "inventory_tag_id")).isTrue();
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

    private boolean columnExists(DataSource dataSource, String table, String column) throws Exception {
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            return rs.next();
        }
    }
}
