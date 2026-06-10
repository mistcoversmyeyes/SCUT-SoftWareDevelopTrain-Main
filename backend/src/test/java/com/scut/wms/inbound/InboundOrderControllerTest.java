package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scut.wms.masterdata.StorageLocation;
import com.scut.wms.masterdata.StorageLocationMapper;
import com.scut.wms.masterdata.Warehouse;
import com.scut.wms.masterdata.WarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
    private StorageLocationMapper storageLocationMapper;

    @BeforeEach
    void cleanCreatedData() {
        List<Long> orderIds = inboundOrderMapper.selectList(new QueryWrapper<InboundOrder>().gt("id", 1))
                .stream()
                .map(InboundOrder::getId)
                .toList();
        if (!orderIds.isEmpty()) {
            kanbanBoardMapper.delete(new QueryWrapper<KanbanBoard>().in("inbound_order_id", orderIds));
            inboundOrderLineMapper.delete(new QueryWrapper<InboundOrderLine>().in("inbound_order_id", orderIds));
            inboundOrderMapper.delete(new QueryWrapper<InboundOrder>().in("id", orderIds));
        }
        storageLocationMapper.delete(new QueryWrapper<StorageLocation>().gt("id", 3));
        warehouseMapper.delete(new QueryWrapper<Warehouse>().gt("id", 1));
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
                .andExpect(jsonPath("$.status").value("RELEASED"))
                .andExpect(jsonPath("$.releasedAt").isNotEmpty());

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
                .andExpect(jsonPath("$.status").value("RELEASED"));

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
                  "supplierId": 1,
                  "sourceDocNo": "PO-TDD-MISMATCH",
                  "remark": "mismatch",
                  "lines": [
                    {
                      "materialId": 1,
                      "plannedQty": 1.000,
                      "targetWarehouseId": %d,
                      "targetLocationId": 1
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
                  "supplierId": 2,
                  "sourceDocNo": "%s",
                  "remark": "updated",
                  "lines": [
                    {
                      "materialId": 3,
                      "plannedQty": 7.000,
                      "targetWarehouseId": 1,
                      "targetLocationId": 3
                    }
                  ]
                }
                """.formatted(sourceDocNo);
    }

    private String singleQtyCreateRequest(String sourceDocNo, String plannedQty) {
        return """
                {
                  "supplierId": 1,
                  "sourceDocNo": "%s",
                  "remark": "invalid quantity",
                  "lines": [
                    {
                      "materialId": 1,
                      "plannedQty": %s,
                      "targetWarehouseId": 1,
                      "targetLocationId": 1
                    }
                  ]
                }
                """.formatted(sourceDocNo, plannedQty);
    }

    private String defaultCreateRequest(String sourceDocNo) {
        return """
                {
                  "supplierId": 1,
                  "sourceDocNo": "%s",
                  "remark": "created by test",
                  "lines": [
                    {
                      "materialId": 1,
                      "plannedQty": 12.500,
                      "targetWarehouseId": 1,
                      "targetLocationId": 1
                    },
                    {
                      "materialId": 2,
                      "plannedQty": 8.000,
                      "targetWarehouseId": 1,
                      "targetLocationId": 2
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
