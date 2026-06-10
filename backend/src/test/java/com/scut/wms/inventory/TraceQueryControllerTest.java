package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraceQueryControllerTest {
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

        inboundOrderMapper.update(null, Wrappers.<InboundOrder>lambdaUpdate()
                .eq(InboundOrder::getId, DEMO_ORDER_ID)
                .set(InboundOrder::getStatus, "RELEASED")
                .set(InboundOrder::getCompletedAt, null));

        resetLine(DEMO_LINE_ONE_ID);
        resetLine(DEMO_LINE_TWO_ID);
        resetBoard(DEMO_BOARD_ONE_ID, DEMO_BOARD_ONE_CODE);
        resetBoard(DEMO_BOARD_TWO_ID, DEMO_BOARD_TWO_CODE);
    }

    @Test
    void balancesReturnsScannedStockRows() throws Exception {
        mockMvc.perform(get("/api/inventory/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        scanInbound(DEMO_BOARD_ONE_CODE);

        mockMvc.perform(get("/api/inventory/balances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].materialCode").value("5HG 807 109 C"))
                .andExpect(jsonPath("$[0].materialName").value("前保险杠支架"))
                .andExpect(jsonPath("$[0].warehouseCode").value("WH-JY"))
                .andExpect(jsonPath("$[0].warehouseName").value("吉耀仓"))
                .andExpect(jsonPath("$[0].locationCode").value("A-01"))
                .andExpect(jsonPath("$[0].locationName").value("A区 01 库位"))
                .andExpect(jsonPath("$[0].onHandQty").value(120.0))
                .andExpect(jsonPath("$[0].updatedAt").isNotEmpty());
    }

    @Test
    void movementsReturnsDisplayRowsForScannedKanbans() throws Exception {
        scanInbound(DEMO_BOARD_ONE_CODE);

        mockMvc.perform(get("/api/inventory/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movementNo").isNotEmpty())
                .andExpect(jsonPath("$[0].materialCode").value("5HG 807 109 C"))
                .andExpect(jsonPath("$[0].materialName").value("前保险杠支架"))
                .andExpect(jsonPath("$[0].warehouseCode").value("WH-JY"))
                .andExpect(jsonPath("$[0].warehouseName").value("吉耀仓"))
                .andExpect(jsonPath("$[0].locationCode").value("A-01"))
                .andExpect(jsonPath("$[0].locationName").value("A区 01 库位"))
                .andExpect(jsonPath("$[0].qty").value(120.0))
                .andExpect(jsonPath("$[0].kanbanCode").value(DEMO_BOARD_ONE_CODE))
                .andExpect(jsonPath("$[0].inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$[0].occurredAt").isNotEmpty());
    }

    @Test
    void kanbanTraceReturnsPrintedStateBeforeScanAndMovementAfterScan() throws Exception {
        mockMvc.perform(get("/api/kanbans/{kanbanCode}/trace", DEMO_BOARD_TWO_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kanbanCode").value(DEMO_BOARD_TWO_CODE))
                .andExpect(jsonPath("$.kanbanStatus").value("PRINTED"))
                .andExpect(jsonPath("$.inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$.materialCode").value("5WD 723 913 C"))
                .andExpect(jsonPath("$.materialName").value("踏板组件"))
                .andExpect(jsonPath("$.locationCode").value("A-02"))
                .andExpect(jsonPath("$.locationName").value("A区 02 库位"))
                .andExpect(jsonPath("$.scannedAt").isEmpty())
                .andExpect(jsonPath("$.movementNo").isEmpty());

        scanInbound(DEMO_BOARD_TWO_CODE);

        mockMvc.perform(get("/api/kanbans/{kanbanCode}/trace", DEMO_BOARD_TWO_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kanbanCode").value(DEMO_BOARD_TWO_CODE))
                .andExpect(jsonPath("$.kanbanStatus").value("RECEIVED"))
                .andExpect(jsonPath("$.inboundNo").value("IN-20260610-001"))
                .andExpect(jsonPath("$.materialCode").value("5WD 723 913 C"))
                .andExpect(jsonPath("$.materialName").value("踏板组件"))
                .andExpect(jsonPath("$.locationCode").value("A-02"))
                .andExpect(jsonPath("$.locationName").value("A区 02 库位"))
                .andExpect(jsonPath("$.scannedAt").isNotEmpty())
                .andExpect(jsonPath("$.movementNo").isNotEmpty());
    }

    private void scanInbound(String kanbanCode) throws Exception {
        mockMvc.perform(post("/api/inventory/scan-inbound")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scanRequest(kanbanCode)))
                .andExpect(status().isOk());
    }

    private void resetLine(Long lineId) {
        InboundOrderLine line = inboundOrderLineMapper.selectById(lineId);
        line.setReceivedQty(BigDecimal.ZERO);
        inboundOrderLineMapper.updateById(line);
    }

    private void resetBoard(Long boardId, String kanbanCode) {
        kanbanBoardMapper.update(null, Wrappers.<KanbanBoard>lambdaUpdate()
                .eq(KanbanBoard::getId, boardId)
                .set(KanbanBoard::getKanbanCode, kanbanCode)
                .set(KanbanBoard::getStatus, "PRINTED")
                .set(KanbanBoard::getReceivedAt, null));
    }

    private String scanRequest(String kanbanCode) {
        return """
                {
                  "kanbanCode": "%s"
                }
                """.formatted(kanbanCode);
    }
}
