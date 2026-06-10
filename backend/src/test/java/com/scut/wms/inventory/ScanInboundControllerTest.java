package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.inbound.InboundOrder;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.InboundOrderMapper;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanInboundControllerTest {
    private static final Long DEMO_ORDER_ID = 1L;
    private static final Long DEMO_LINE_ONE_ID = 1L;
    private static final Long DEMO_LINE_TWO_ID = 2L;
    private static final Long DEMO_BOARD_ONE_ID = 1L;
    private static final Long DEMO_BOARD_TWO_ID = 2L;
    private static final String DEMO_BOARD_ONE_CODE = "KB:v1:IN-20260610-001:1:1";
    private static final String DEMO_BOARD_TWO_CODE = "KB:v1:IN-20260610-001:2:1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryMovementMapper inventoryMovementMapper;

    @Autowired
    private InventoryBalanceMapper inventoryBalanceMapper;

    @Autowired
    private KanbanBoardMapper kanbanBoardMapper;

    @Autowired
    private InboundOrderLineMapper inboundOrderLineMapper;

    @Autowired
    private InboundOrderMapper inboundOrderMapper;

    @BeforeEach
    void resetDemoState() {
        inventoryMovementMapper.delete(new QueryWrapper<>());
        inventoryBalanceMapper.delete(new QueryWrapper<>());

        inboundOrderMapper.update(
                null,
                Wrappers.<InboundOrder>lambdaUpdate()
                        .eq(InboundOrder::getId, DEMO_ORDER_ID)
                        .set(InboundOrder::getStatus, "RELEASED")
                        .set(InboundOrder::getCompletedAt, (String) null)
        );

        resetLine(DEMO_LINE_ONE_ID);
        resetLine(DEMO_LINE_TWO_ID);
        resetBoard(DEMO_BOARD_ONE_ID, DEMO_BOARD_ONE_CODE);
        resetBoard(DEMO_BOARD_TWO_ID, DEMO_BOARD_TWO_CODE);
    }

    @Test
    void scanPrintedKanbanCreatesMovementUpdatesBalanceAndMarksOrderPartialReceived() throws Exception {
        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(DEMO_BOARD_ONE_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kanbanCode").value(DEMO_BOARD_ONE_CODE))
                .andExpect(jsonPath("$.inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$.materialCode").value("5HG 807 109 C"))
                .andExpect(jsonPath("$.materialName").value("前保险杠支架"))
                .andExpect(jsonPath("$.receivedQty").value(120.0))
                .andExpect(jsonPath("$.locationName").value("A区 01 库位"))
                .andExpect(jsonPath("$.orderStatus").value("PARTIAL_RECEIVED"))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());

        List<InventoryMovement> movements = inventoryMovementMapper.selectList(new QueryWrapper<>());
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getMovementType()).isEqualTo("INBOUND_RECEIVE");
        assertThat(movements.get(0).getSourceType()).isEqualTo("KANBAN_BOARD");
        assertThat(movements.get(0).getSourceId()).isEqualTo(DEMO_BOARD_ONE_ID);
        assertThat(movements.get(0).getKanbanBoardId()).isEqualTo(DEMO_BOARD_ONE_ID);
        assertThat(movements.get(0).getQty()).isEqualByComparingTo("120.000");

        List<InventoryBalance> balances = inventoryBalanceMapper.selectList(new QueryWrapper<>());
        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).getMaterialId()).isEqualTo(1L);
        assertThat(balances.get(0).getWarehouseId()).isEqualTo(1L);
        assertThat(balances.get(0).getStorageLocationId()).isEqualTo(1L);
        assertThat(balances.get(0).getOnHandQty()).isEqualByComparingTo("120.000");

        KanbanBoard board = kanbanBoardMapper.selectById(DEMO_BOARD_ONE_ID);
        assertThat(board.getStatus()).isEqualTo("RECEIVED");
        assertThat(board.getReceivedAt()).isNotNull();

        InboundOrderLine line = inboundOrderLineMapper.selectById(DEMO_LINE_ONE_ID);
        assertThat(line.getReceivedQty()).isEqualByComparingTo("120.000");

        InboundOrder order = inboundOrderMapper.selectById(DEMO_ORDER_ID);
        assertThat(order.getStatus()).isEqualTo("PARTIAL_RECEIVED");
        assertThat(order.getCompletedAt()).isNull();
    }

    @Test
    void scanSecondKanbanCompletesOrder() throws Exception {
        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(DEMO_BOARD_ONE_CODE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(DEMO_BOARD_TWO_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kanbanCode").value(DEMO_BOARD_TWO_CODE))
                .andExpect(jsonPath("$.inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$.materialCode").value("5WD 723 913 C"))
                .andExpect(jsonPath("$.materialName").value("踏板组件"))
                .andExpect(jsonPath("$.receivedQty").value(80.0))
                .andExpect(jsonPath("$.locationName").value("A区 02 库位"))
                .andExpect(jsonPath("$.orderStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.receivedAt").isNotEmpty());

        InboundOrder order = inboundOrderMapper.selectById(DEMO_ORDER_ID);
        assertThat(order.getStatus()).isEqualTo("COMPLETED");
        assertThat(order.getCompletedAt()).isNotNull();
        assertThat(inboundOrderLineMapper.selectById(DEMO_LINE_TWO_ID).getReceivedQty())
                .isEqualByComparingTo("80.000");
    }

    @Test
    void scanReceivedKanbanReturnsBusinessErrorWithoutDuplicatingStock() throws Exception {
        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(DEMO_BOARD_ONE_CODE)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(DEMO_BOARD_ONE_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("重复扫码"));

        assertThat(inventoryMovementMapper.selectCount(new QueryWrapper<>())).isEqualTo(1);
        assertThat(inventoryBalanceMapper.selectList(new QueryWrapper<>()))
                .singleElement()
                .extracting(InventoryBalance::getOnHandQty)
                .isEqualTo(new BigDecimal("120.000"));
    }

    @Test
    void scanMissingKanbanReturnsBusinessErrorWithoutPersistingMovement() throws Exception {
        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest("KB:v1:missing")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未找到看板"));

        assertThat(inventoryMovementMapper.selectCount(new QueryWrapper<>())).isZero();
        assertThat(inventoryBalanceMapper.selectCount(new QueryWrapper<>())).isZero();
    }

    @Test
    void scanPrintedKanbanRejectsDisallowedOrderStatus() throws Exception {
        InboundOrder order = inboundOrderMapper.selectById(DEMO_ORDER_ID);
        order.setStatus("CANCELLED");
        inboundOrderMapper.updateById(order);

        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(DEMO_BOARD_ONE_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("单据状态不允许入库"));

        assertThat(inventoryMovementMapper.selectCount(new QueryWrapper<>())).isZero();
        assertThat(kanbanBoardMapper.selectById(DEMO_BOARD_ONE_ID).getStatus()).isEqualTo("PRINTED");
    }

    private void resetLine(Long lineId) {
        InboundOrderLine line = inboundOrderLineMapper.selectById(lineId);
        line.setReceivedQty(BigDecimal.ZERO);
        inboundOrderLineMapper.updateById(line);
    }

    private void resetBoard(Long boardId, String kanbanCode) {
        KanbanBoard board = kanbanBoardMapper.selectById(boardId);
        board.setKanbanCode(kanbanCode);
        board.setStatus("PRINTED");
        board.setReceivedAt(null);
        kanbanBoardMapper.updateById(board);
    }

    private String scanRequest(String kanbanCode) {
        return """
                {
                  "kanbanCode": "%s"
                }
                """.formatted(kanbanCode);
    }
}
