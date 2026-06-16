package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scut.wms.masterdata.MaterialContainerType;
import com.scut.wms.masterdata.MaterialContainerTypeMapper;
import com.scut.wms.masterdata.StorageLocation;
import com.scut.wms.masterdata.Warehouse;
import com.scut.wms.masterdata.WarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InboundOrderControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InboundOrderMapper inboundOrderMapper;

    @Autowired
    private InboundOrderLineMapper inboundOrderLineMapper;

    @Autowired
    private KanbanBoardMapper kanbanBoardMapper;

    @Autowired
    private WarehouseMapper warehouseMapper;

    @Autowired
    private MaterialContainerTypeMapper materialContainerTypeMapper;

    @BeforeEach
    void fixExistingDemoData() {
        // Seed material-container type associations for tests
        seedMaterialContainerType(1L, 1L);
        seedMaterialContainerType(2L, 1L);
        seedMaterialContainerType(3L, 2L);

        // Fix existing demo data
        // Fix board location_id/container_type_id for existing demo data (may still have default 0 values)
        KanbanBoard board1 = kanbanBoardMapper.selectById(1L);
        if (board1 != null) {
            board1.setLocationId(1L);
            board1.setContainerTypeId(1L);
            kanbanBoardMapper.updateById(board1);
        }
        KanbanBoard board2 = kanbanBoardMapper.selectById(2L);
        if (board2 != null) {
            board2.setLocationId(2L);
            board2.setContainerTypeId(1L);
            kanbanBoardMapper.updateById(board2);
        }
        // Fix line container_type_id for existing demo data
        InboundOrderLine line1 = inboundOrderLineMapper.selectById(1L);
        if (line1 != null) {
            line1.setContainerTypeId(1L);
            inboundOrderLineMapper.updateById(line1);
        }
        InboundOrderLine line2 = inboundOrderLineMapper.selectById(2L);
        if (line2 != null) {
            line2.setContainerTypeId(1L);
            inboundOrderLineMapper.updateById(line2);
        }
    }

    @Test
    void createOrderWithTwoLinesReturnsDraftAndPersistsLines() throws Exception {
        JsonNode response = performCreate(defaultCreateRequest("PO-TDD-CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lineCount").value(2))
                .andExpect(jsonPath("$.plannedQty").value(20.5))
                .andReturnAsJson();

        Long orderId = response.get("id").asLong();
        InboundOrder order = inboundOrderMapper.selectById(orderId);
        List<InboundOrderLine> lines = linesOf(orderId);

        assertThat(order.getStatus()).isEqualTo("DRAFT");
        assertThat(order.getInboundNo()).startsWith("IN-");
        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(InboundOrderLine::getLineNo).containsExactly(1, 2);
        assertThat(lines).extracting(InboundOrderLine::getMaterialId).containsExactly(1L, 2L);
    }

    @Test
    void updateDraftOrderChangesLines() throws Exception {
        Long orderId = createOrder("PO-TDD-UPDATE");

        mockMvc.perform(put("/api/inbound-orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneLineUpdateRequest("PO-TDD-UPDATED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplier.id").value(2))
                .andExpect(jsonPath("$.sourceDocNo").value("PO-TDD-UPDATED"))
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.plannedQty").value(7.0));

        InboundOrder order = inboundOrderMapper.selectById(orderId);
        List<InboundOrderLine> lines = linesOf(orderId);

        assertThat(order.getSupplierId()).isEqualTo(2L);
        assertThat(order.getSourceDocNo()).isEqualTo("PO-TDD-UPDATED");
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getLineNo()).isEqualTo(1);
        assertThat(lines.get(0).getMaterialId()).isEqualTo(3L);
        assertThat(lines.get(0).getPlannedQty()).isEqualByComparingTo("7.000");
    }

    @Test
    void updateReleasedUnreceivedOrderRegeneratesKanbansWithoutDuplicates() throws Exception {
        Long orderId = createReleasedOrder("PO-TDD-UPDATE-RELEASED");
        assertThat(kanbansOf(orderId)).hasSize(2);

        mockMvc.perform(put("/api/inbound-orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneLineUpdateRequest("PO-TDD-RELEASED-UPDATED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.plannedQty").value(7.0));

        InboundOrder order = inboundOrderMapper.selectById(orderId);
        List<InboundOrderLine> lines = linesOf(orderId);
        List<KanbanBoard> kanbans = kanbansOf(orderId);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).getLineNo()).isEqualTo(1);
        assertThat(lines.get(0).getMaterialId()).isEqualTo(3L);
        assertThat(kanbans).hasSize(1);
        assertThat(kanbans).extracting(KanbanBoard::getInboundOrderLineId).containsExactly(lines.get(0).getId());
        assertThat(kanbans).extracting(KanbanBoard::getStatus).containsOnly("PRINTED");
        assertThat(kanbans).extracting(KanbanBoard::getKanbanCode)
                .containsExactly("KB:v1:%s:1:1".formatted(order.getInboundNo()));
    }

    @Test
    void updateRejectsReleasedOrderWithReceivedQuantity() throws Exception {
        Long orderId = createReleasedOrder("PO-TDD-UPDATE-RECEIVED-QTY");
        InboundOrderLine line = linesOf(orderId).get(0);
        line.setReceivedQty(new BigDecimal("1.000"));
        inboundOrderLineMapper.updateById(line);

        mockMvc.perform(put("/api/inbound-orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneLineUpdateRequest("PO-TDD-RECEIVED-QTY-UPDATED")))
                .andExpect(status().isBadRequest());

        assertThat(linesOf(orderId)).hasSize(2);
        assertThat(kanbansOf(orderId)).hasSize(2);
    }

    @Test
    void updateRejectsReleasedOrderWithReceivedKanban() throws Exception {
        Long orderId = createReleasedOrder("PO-TDD-UPDATE-RECEIVED-KB");
        markFirstKanbanReceived(orderId);

        mockMvc.perform(put("/api/inbound-orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oneLineUpdateRequest("PO-TDD-RECEIVED-KB-UPDATED")))
                .andExpect(status().isBadRequest());

        assertThat(linesOf(orderId)).hasSize(2);
        assertThat(kanbansOf(orderId)).hasSize(2);
    }

    @Test
    void releaseGeneratesKanbanRowsAndChangesStatusToReleased() throws Exception {
        Long orderId = createOrder("PO-TDD-RELEASE");

        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("RELEASED"))
                .andExpect(jsonPath("$.order.releasedAt").isNotEmpty());

        InboundOrder order = inboundOrderMapper.selectById(orderId);
        List<KanbanBoard> kanbans = kanbansOf(orderId);

        assertThat(order.getStatus()).isEqualTo("RELEASED");
        assertThat(order.getReleasedAt()).isNotNull();
        assertThat(kanbans).hasSize(2);
        assertThat(kanbans).extracting(KanbanBoard::getStatus).containsOnly("PRINTED");
        assertThat(kanbans).extracting(KanbanBoard::getKanbanCode)
                .allSatisfy(code -> assertThat(code).startsWith("KB:v1:" + order.getInboundNo() + ":"));
    }

    @Test
    void releaseTwiceDoesNotDuplicateKanbans() throws Exception {
        Long orderId = createOrder("PO-TDD-IDEMPOTENT");

        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("RELEASED"));

        assertThat(kanbansOf(orderId)).hasSize(2);
    }

    @Test
    void releaseRejectsCompletedOrder() throws Exception {
        Long orderId = createOrder("PO-TDD-RELEASE-COMPLETED");
        updateOrderStatus(orderId, "COMPLETED");

        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isBadRequest());

        assertThat(inboundOrderMapper.selectById(orderId).getStatus()).isEqualTo("COMPLETED");
        assertThat(kanbansOf(orderId)).isEmpty();
    }

    @Test
    void releaseRejectsPartialReceivedOrder() throws Exception {
        Long orderId = createOrder("PO-TDD-RELEASE-PARTIAL");
        updateOrderStatus(orderId, "PARTIAL_RECEIVED");

        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isBadRequest());

        assertThat(inboundOrderMapper.selectById(orderId).getStatus()).isEqualTo("PARTIAL_RECEIVED");
        assertThat(kanbansOf(orderId)).isEmpty();
    }

    @Test
    void cancelReleasedOrderWithNoReceivedQuantityMarksOrderAndKanbansCancelled() throws Exception {
        Long orderId = createOrder("PO-TDD-CANCEL");
        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inbound-orders/{id}/cancel", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        InboundOrder order = inboundOrderMapper.selectById(orderId);
        List<KanbanBoard> kanbans = kanbansOf(orderId);

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(kanbans).hasSize(2);
        assertThat(kanbans).extracting(KanbanBoard::getStatus).containsOnly("CANCELLED");
    }

    @Test
    void cancelRejectsOrderWithReceivedQuantity() throws Exception {
        Long orderId = createReleasedOrder("PO-TDD-CANCEL-RECEIVED-QTY");
        InboundOrderLine line = linesOf(orderId).get(0);
        line.setReceivedQty(new BigDecimal("1.000"));
        inboundOrderLineMapper.updateById(line);

        mockMvc.perform(post("/api/inbound-orders/{id}/cancel", orderId))
                .andExpect(status().isBadRequest());

        assertThat(inboundOrderMapper.selectById(orderId).getStatus()).isEqualTo("RELEASED");
        assertThat(kanbansOf(orderId)).extracting(KanbanBoard::getStatus).containsOnly("PRINTED");
    }

    @Test
    void cancelRejectsOrderWithReceivedKanban() throws Exception {
        Long orderId = createReleasedOrder("PO-TDD-CANCEL-RECEIVED-KB");
        markFirstKanbanReceived(orderId);

        mockMvc.perform(post("/api/inbound-orders/{id}/cancel", orderId))
                .andExpect(status().isBadRequest());

        assertThat(inboundOrderMapper.selectById(orderId).getStatus()).isEqualTo("RELEASED");
    }

    @Test
    void invalidLocationWarehouseMismatchReturnsBadRequest() throws Exception {
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseCode("WH-TDD");
        warehouse.setWarehouseName("测试仓");
        warehouse.setStatus("ENABLED");
        warehouseMapper.insert(warehouse);

        String request = """
                {
                  "sourceDocNo": "PO-TDD-MISMATCH",
                  "remark": "mismatch",
                  "lines": [
                    {
                      "supplierId": 1,
                      "materialId": 1,
                      "plannedQty": 1.000,
                      "targetWarehouseId": %d,
                      "targetLocationId": 1,
                      "containerTypeId": 1
                    }
                  ]
                }
                """.formatted(warehouse.getId());

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroOrNegativePlannedQtyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleQtyCreateRequest("PO-TDD-ZERO-QTY", "0.000")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/inbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleQtyCreateRequest("PO-TDD-NEGATIVE-QTY", "-1.000")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersOrdersAndReturnsAggregatedDisplayFields() throws Exception {
        JsonNode created = performCreate(defaultCreateRequest("PO-TDD-LIST"))
                .andExpect(status().isOk())
                .andReturnAsJson();

        mockMvc.perform(get("/api/inbound-orders")
                        .param("status", "DRAFT")
                        .param("inboundNo", created.get("inboundNo").asText())
                        .param("supplier", "华翔"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(created.get("id").asLong()))
                .andExpect(jsonPath("$[0].supplier.name").value("佛山华翔金属件 8KH"))
                .andExpect(jsonPath("$[0].lineCount").value(2))
                .andExpect(jsonPath("$[0].plannedQty").value(20.5))
                .andExpect(jsonPath("$[0].receivedQty").value(0));
    }

    @Test
    void printOrderReturnsDisplayHeaderAndLines() throws Exception {
        mockMvc.perform(get("/api/inbound-orders/{id}/print", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$.supplierCode").value("8KH"))
                .andExpect(jsonPath("$.supplierName").value("佛山华翔金属件 8KH"))
                .andExpect(jsonPath("$.sourceDocNo").value("PO-20260610-001"))
                .andExpect(jsonPath("$.status").isString())
                .andExpect(jsonPath("$.remark").value("Week 2 采购入库演示单据"))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[0].materialCode").value("5HG 807 109 C"))
                .andExpect(jsonPath("$.lines[0].warehouseName").value("吉耀仓"))
                .andExpect(jsonPath("$.lines[0].locationName").value("A区 01 库位"))
                .andExpect(jsonPath("$.lines[1].lineNo").value(2))
                .andExpect(jsonPath("$.lines[1].materialCode").value("5WD 723 913 C"));
    }

    @Test
    void printKanbansReturnsDisplayLabels() throws Exception {
        mockMvc.perform(get("/api/inbound-orders/{id}/kanbans/print", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kanbanCode").value("KB:v1:IN-20260610-001:1:1"))
                .andExpect(jsonPath("$[0].inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$[0].supplierCode").value("8KH"))
                .andExpect(jsonPath("$[0].materialCode").value("5HG 807 109 C"))
                .andExpect(jsonPath("$[0].materialName").value("前保险杠支架"))
                .andExpect(jsonPath("$[0].locationName").value("A区 01 库位"))
                .andExpect(jsonPath("$[0].qty").value(120.0))
                .andExpect(jsonPath("$[0].status").value("PRINTED"))
                .andExpect(jsonPath("$[1].kanbanCode").value("KB:v1:IN-20260610-001:2:1"))
                .andExpect(jsonPath("$[1].materialCode").value("5WD 723 913 C"));
    }

    @Test
    void printEndpointsReturnNotFoundForMissingOrder() throws Exception {
        mockMvc.perform(get("/api/inbound-orders/{id}/print", 9999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/inbound-orders/{id}/kanbans/print", 9999L))
                .andExpect(status().isNotFound());
    }

    private Long createOrder(String sourceDocNo) throws Exception {
        return performCreate(defaultCreateRequest(sourceDocNo))
                .andExpect(status().isOk())
                .andReturnAsJson()
                .get("id")
                .asLong();
    }

    private Long createReleasedOrder(String sourceDocNo) throws Exception {
        Long orderId = createOrder(sourceDocNo);
        mockMvc.perform(post("/api/inbound-orders/{id}/release", orderId))
                .andExpect(status().isOk());
        return orderId;
    }

    private void updateOrderStatus(Long orderId, String status) {
        InboundOrder order = inboundOrderMapper.selectById(orderId);
        order.setStatus(status);
        inboundOrderMapper.updateById(order);
    }

    private void markFirstKanbanReceived(Long orderId) {
        KanbanBoard kanban = kanbansOf(orderId).get(0);
        kanban.setStatus("RECEIVED");
        kanban.setReceivedAt(LocalDateTime.now());
        kanbanBoardMapper.updateById(kanban);
    }

    private ResultActionsJson performCreate(String request) throws Exception {
        return new ResultActionsJson(mockMvc.perform(post("/api/inbound-orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)));
    }

    private String oneLineUpdateRequest(String sourceDocNo) {
        return """
                {
                  "sourceDocNo": "%s",
                  "remark": "updated",
                  "lines": [
                    {
                      "supplierId": 2,
                      "materialId": 3,
                      "plannedQty": 7.000,
                      "targetWarehouseId": 1,
                      "targetLocationId": 3,
                      "containerTypeId": 2
                    }
                  ]
                }
                """.formatted(sourceDocNo);
    }

    private String singleQtyCreateRequest(String sourceDocNo, String plannedQty) {
        return """
                {
                  "sourceDocNo": "%s",
                  "remark": "invalid quantity",
                  "lines": [
                    {
                      "supplierId": 1,
                      "materialId": 1,
                      "plannedQty": %s,
                      "targetWarehouseId": 1,
                      "targetLocationId": 1,
                      "containerTypeId": 1
                    }
                  ]
                }
                """.formatted(sourceDocNo, plannedQty);
    }

    private void seedMaterialContainerType(Long materialId, Long containerTypeId) {
        Long count = materialContainerTypeMapper.selectCount(Wrappers.<MaterialContainerType>lambdaQuery()
                .eq(MaterialContainerType::getMaterialId, materialId)
                .eq(MaterialContainerType::getContainerTypeId, containerTypeId));
        if (count == 0) {
            MaterialContainerType mct = new MaterialContainerType();
            mct.setMaterialId(materialId);
            mct.setContainerTypeId(containerTypeId);
            mct.setIsDefault(1);
            materialContainerTypeMapper.insert(mct);
        }
    }

    private String defaultCreateRequest(String sourceDocNo) {
        return """
                {
                  "sourceDocNo": "%s",
                  "remark": "created by test",
                  "lines": [
                    {
                      "supplierId": 1,
                      "materialId": 1,
                      "plannedQty": 12.500,
                      "targetWarehouseId": 1,
                      "targetLocationId": 1,
                      "containerTypeId": 1
                    },
                    {
                      "supplierId": 1,
                      "materialId": 2,
                      "plannedQty": 8.000,
                      "targetWarehouseId": 1,
                      "targetLocationId": 2,
                      "containerTypeId": 1
                    }
                  ]
                }
                """.formatted(sourceDocNo);
    }

    private List<InboundOrderLine> linesOf(Long orderId) {
        return inboundOrderLineMapper.selectList(new QueryWrapper<InboundOrderLine>()
                .eq("inbound_order_id", orderId)
                .orderByAsc("line_no"));
    }

    private List<KanbanBoard> kanbansOf(Long orderId) {
        return kanbanBoardMapper.selectList(new QueryWrapper<KanbanBoard>()
                .eq("inbound_order_id", orderId)
                .orderByAsc("id"));
    }

    private final class ResultActionsJson {
        private final org.springframework.test.web.servlet.ResultActions actions;

        private ResultActionsJson(org.springframework.test.web.servlet.ResultActions actions) {
            this.actions = actions;
        }

        private ResultActionsJson andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        private JsonNode andReturnAsJson() throws Exception {
            byte[] content = actions.andReturn().getResponse().getContentAsByteArray();
            return objectMapper.readTree(new String(content, StandardCharsets.UTF_8));
        }
    }
}
