package com.scut.wms.aiwarning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowHistoryImportControllerTest {
    private static final String SUPPLIER_CODE = "AI-SUP-01";
    private static final String MATERIAL_ONE_CODE = "AI.MAT.001";
    private static final String MATERIAL_TWO_CODE = "AI.MAT.002";
    private static final String WAREHOUSE_CODE = "WH-AI";
    private static final String LOCATION_ONE_CODE = "AI-01";
    private static final String LOCATION_TWO_CODE = "AI-02";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        ensureReferenceSchema();
        deleteIfExists("DELETE FROM ai_inventory_flow_history");
        deleteIfExists("DELETE FROM ai_import_batch");
        ensureReferenceData();
    }

    @Test
    void importInventoryFlowHistoryReturnsSummaryAndRowErrors() throws Exception {
        MockMultipartFile file = csvFile("""
                business_date,material_code,warehouse_code,location_code,board_code,movement_type,quantity,source_order_no,quality_status
                2026-06-01,AI.MAT.001,WH-AI,AI-01,KB-AI-001,INBOUND,100,IN-AI-001,NORMAL
                2026-06-10,AI.MAT.001,WH-AI,AI-01,KB-AI-001,OUTBOUND,40,OUT-AI-001,NORMAL
                2026-06-12,AI.MAT.MISSING,WH-AI,AI-01,KB-AI-003,OUTBOUND,12,OUT-AI-003,HOLD
                """);

        mockMvc.perform(multipart("/api/ai-warning/imports/inventory-flow-history")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importObject").value("inventory_flow_history"))
                .andExpect(jsonPath("$.fileName").value("inventory-flow-history.csv"))
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.successRows").value(2))
                .andExpect(jsonPath("$.failedRows").value(1))
                .andExpect(jsonPath("$.batchId").isNumber())
                .andExpect(jsonPath("$.summary.materialCount").value(1))
                .andExpect(jsonPath("$.summary.movementTypeCounts.INBOUND").value(1))
                .andExpect(jsonPath("$.summary.movementTypeCounts.OUTBOUND").value(1))
                .andExpect(jsonPath("$.summary.businessDateStart").value("2026-06-01"))
                .andExpect(jsonPath("$.summary.businessDateEnd").value("2026-06-10"))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(4))
                .andExpect(jsonPath("$.errors[0].field").value("material_code"))
                .andExpect(jsonPath("$.errors[0].message").value("物料编码不存在"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").value("AI.MAT.MISSING"));
    }

    @Test
    void listEndpointsExposeImportedBatchesAndRecords() throws Exception {
        mockMvc.perform(multipart("/api/ai-warning/imports/inventory-flow-history")
                        .file(csvFile("""
                                business_date,material_code,warehouse_code,location_code,board_code,movement_type,quantity,source_order_no,quality_status
                                2026-06-01,AI.MAT.001,WH-AI,AI-01,KB-AI-001,INBOUND,100,IN-AI-001,NORMAL
                                2026-06-08,AI.MAT.002,WH-AI,AI-02,KB-AI-002,SEAL,20,SEAL-AI-001,HOLD
                                """))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk());

        Long batchId = jdbcTemplate.queryForObject("SELECT id FROM ai_import_batch ORDER BY id DESC LIMIT 1", Long.class);

        mockMvc.perform(get("/api/ai-warning/imports/inventory-flow-history/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchId").value(batchId))
                .andExpect(jsonPath("$[0].importObject").value("inventory_flow_history"))
                .andExpect(jsonPath("$[0].successRows").value(2))
                .andExpect(jsonPath("$[0].failedRows").value(0));

        mockMvc.perform(get("/api/ai-warning/imports/inventory-flow-history/records")
                        .param("batchId", String.valueOf(batchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].batchId").value(batchId))
                .andExpect(jsonPath("$[0].rowNumber").value(3))
                .andExpect(jsonPath("$[0].materialCode").value(MATERIAL_TWO_CODE))
                .andExpect(jsonPath("$[0].movementType").value("SEAL"))
                .andExpect(jsonPath("$[0].locationCode").value(LOCATION_TWO_CODE))
                .andExpect(jsonPath("$[1].rowNumber").value(2))
                .andExpect(jsonPath("$[1].materialCode").value(MATERIAL_ONE_CODE))
                .andExpect(jsonPath("$[1].movementType").value("INBOUND"));
    }

    private void ensureReferenceSchema() {
        ensureColumn("material", "low_stock_qty", "DECIMAL(18, 3) DEFAULT NULL");
        ensureColumn("material", "high_stock_qty", "DECIMAL(18, 3) DEFAULT NULL");
        ensureColumn("storage_location", "max_capacity", "DECIMAL(10, 2) DEFAULT NULL");
    }

    private void ensureColumn(String tableName, String columnName, String definition) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private void ensureReferenceData() {
        long supplierId = ensureSupplier();
        long warehouseId = ensureWarehouse();
        ensureLocation(warehouseId, LOCATION_ONE_CODE, "AI 测试库位 1");
        ensureLocation(warehouseId, LOCATION_TWO_CODE, "AI 测试库位 2");
        ensureMaterial(MATERIAL_ONE_CODE, "AI测试物料一", supplierId, 30, 120);
        ensureMaterial(MATERIAL_TWO_CODE, "AI测试物料二", supplierId, 15, 60);
    }

    private long ensureSupplier() {
        jdbcTemplate.update(
                "INSERT INTO supplier (supplier_code, supplier_name, status) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE supplier_name = VALUES(supplier_name), status = VALUES(status)",
                SUPPLIER_CODE,
                "AI 导入测试供应商",
                "ENABLED"
        );
        return jdbcTemplate.queryForObject("SELECT id FROM supplier WHERE supplier_code = ?", Long.class, SUPPLIER_CODE);
    }

    private long ensureWarehouse() {
        jdbcTemplate.update(
                "INSERT INTO warehouse (warehouse_code, warehouse_name, status) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE warehouse_name = VALUES(warehouse_name), status = VALUES(status)",
                WAREHOUSE_CODE,
                "AI 导入测试仓",
                "ENABLED"
        );
        return jdbcTemplate.queryForObject("SELECT id FROM warehouse WHERE warehouse_code = ?", Long.class, WAREHOUSE_CODE);
    }

    private void ensureLocation(long warehouseId, String locationCode, String locationName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM storage_location WHERE warehouse_id = ? AND location_code = ?",
                Integer.class,
                warehouseId,
                locationCode
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO storage_location (warehouse_id, location_code, location_name, max_capacity, status) VALUES (?, ?, ?, ?, ?)",
                warehouseId,
                locationCode,
                locationName,
                100,
                "ENABLED"
        );
    }

    private void ensureMaterial(String materialCode, String materialName, long supplierId, int lowStockQty, int highStockQty) {
        jdbcTemplate.update(
                "INSERT INTO material (material_code, material_name, specification, unit, supplier_id, low_stock_qty, high_stock_qty, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE material_name = VALUES(material_name), supplier_id = VALUES(supplier_id), low_stock_qty = VALUES(low_stock_qty), high_stock_qty = VALUES(high_stock_qty), status = VALUES(status)",
                materialCode,
                materialName,
                "AI 测试规格",
                "件",
                supplierId,
                lowStockQty,
                highStockQty,
                "ENABLED"
        );
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file",
                "inventory-flow-history.csv",
                "text/csv",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void deleteIfExists(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
        }
    }
}
