package com.scut.wms.outbound;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
import com.scut.wms.inventory.InventoryBalance;
import com.scut.wms.inventory.InventoryBalanceMapper;
import com.scut.wms.inventory.InventoryMovementMapper;
import com.scut.wms.lock.InventoryLock;
import com.scut.wms.lock.InventoryLockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Week4BusinessRulesControllerTest {
    private static final Long OUTBOUND_ORDER_ID = 1L;
    private static final Long OUTBOUND_LINE_ONE_ID = 1L;
    private static final Long MATERIAL_ONE_FIFO_BOARD_ID = 1L;
    private static final Long MATERIAL_ONE_NEXT_BOARD_ID = 2L;
    private static final Long MATERIAL_TWO_FIFO_BOARD_ID = 4L;
    private static final String MATERIAL_ONE_FIFO_BOARD_CODE = "IT:v1:IN-20260520-001:1:1";
    private static final String MATERIAL_ONE_NEXT_BOARD_CODE = "IT:v1:IN-20260520-001:1:2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryTagMapper inventoryTagMapper;

    @Autowired
    private InventoryLockMapper inventoryLockMapper;

    @Autowired
    private InventoryMovementMapper inventoryMovementMapper;

    @Autowired
    private InventoryBalanceMapper inventoryBalanceMapper;

    @BeforeEach
    void resetDemoState() {
        inventoryLockMapper.delete(new QueryWrapper<>());
        inventoryMovementMapper.delete(Wrappers.<com.scut.wms.inventory.InventoryMovement>lambdaQuery()
                .ge(com.scut.wms.inventory.InventoryMovement::getId, 9L));

        for (Long boardId : List.of(1L, 2L, 4L, 5L, 13L, 14L, 15L, 16L)) {
            resetBoard(boardId);
        }
    }

    @Test
    void createsOutboundOrderWithContainerTypeFromAcceptancePayload() throws Exception {
        mockMvc.perform(post("/api/outbound-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "purpose": "PICKING",
                                  "sourceDocNo": null,
                                  "remark": "iter4-fr02-regression",
                                  "lines": [
                                    {
                                      "supplierId": 1,
                                      "materialId": 2,
                                      "plannedQty": 1000,
                                      "containerTypeId": 2
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lineCount").value(1))
                .andExpect(jsonPath("$.plannedQty").value(1000.0))
                .andExpect(jsonPath("$.lines[0].containerTypeId").value(2));
    }

    @Test
    void sealedInventoryTagIsExcludedFromAutoLockUntilUnsealed() throws Exception {
        mockMvc.perform(post("/api/inventory-tags/{inventoryTagId}/seal", MATERIAL_ONE_FIFO_BOARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "QUALITY_HOLD",
                                  "remark": "待复检",
                                  "operator": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEALED"));

        mockMvc.perform(post("/api/outbound-orders/{id}/release-and-lock", OUTBOUND_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseIds": [1]
                                }
                                """))
                .andExpect(status().isOk());

        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, OUTBOUND_ORDER_ID)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED)
                        .orderByAsc(InventoryLock::getId));
        assertThat(locks).extracting(InventoryLock::getInventoryTagId)
                .contains(MATERIAL_ONE_NEXT_BOARD_ID, MATERIAL_TWO_FIFO_BOARD_ID)
                .doesNotContain(MATERIAL_ONE_FIFO_BOARD_ID);

        mockMvc.perform(post("/api/inventory-tags/{inventoryTagId}/unseal", MATERIAL_ONE_FIFO_BOARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "QUALITY_RELEASED",
                                  "remark": "复检通过",
                                  "operator": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(post("/api/outbound-orders/{id}/reassign", OUTBOUND_ORDER_ID))
                .andExpect(status().isOk());

        List<InventoryLock> locksAfterUnseal = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, OUTBOUND_ORDER_ID)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED)
                        .orderByAsc(InventoryLock::getId));
        assertThat(locksAfterUnseal).extracting(InventoryLock::getInventoryTagId)
                .contains(MATERIAL_ONE_FIFO_BOARD_ID, MATERIAL_TWO_FIFO_BOARD_ID)
                .doesNotContain(MATERIAL_ONE_NEXT_BOARD_ID);
    }

    @Test
    void normalOutboundRejectsSealedInventoryTag() throws Exception {
        mockMvc.perform(post("/api/inventory-tags/{inventoryTagId}/seal", MATERIAL_ONE_FIFO_BOARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "QUALITY_HOLD",
                                  "remark": "待复检",
                                  "operator": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEALED"));

        mockMvc.perform(post("/api/outbound/pick-no-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryTagCode": "%s",
                                  "qty": 10,
                                  "operator": "tester"
                                }
                                """.formatted(MATERIAL_ONE_FIFO_BOARD_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("库存标签已封存，不能普通出库"));
    }

    @Test
    void manualLockRejectsOutboundConflictAndBlocksAutoFifo() throws Exception {
        mockMvc.perform(post("/api/inventory-tags/{inventoryTagId}/manual-lock", MATERIAL_ONE_FIFO_BOARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "DEMO_RESERVE",
                                  "remark": "课堂演示保留",
                                  "operator": "tester"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdType").value("MANUAL_LOCK"));

        mockMvc.perform(post("/api/outbound-orders/{id}/release-and-lock", OUTBOUND_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseIds": [1]
                                }
                                """))
                .andExpect(status().isOk());

        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, OUTBOUND_ORDER_ID)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED)
                        .orderByAsc(InventoryLock::getId));
        assertThat(locks).extracting(InventoryLock::getInventoryTagId)
                .contains(MATERIAL_ONE_NEXT_BOARD_ID, MATERIAL_TWO_FIFO_BOARD_ID)
                .doesNotContain(MATERIAL_ONE_FIFO_BOARD_ID);

        mockMvc.perform(post("/api/inventory-tags/{inventoryTagId}/manual-lock", MATERIAL_ONE_NEXT_BOARD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "DEMO_RESERVE",
                                  "remark": "冲突测试",
                                  "operator": "tester"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("库存标签已被出库单锁定，不能手动锁库"));
    }

    @Test
    void returnsFifoRecommendationForDraftOutboundOrderWithoutLockingInventory() throws Exception {
        mockMvc.perform(get("/api/outbound-orders/{id}/recommendations", OUTBOUND_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outboundOrderId").value(OUTBOUND_ORDER_ID))
                .andExpect(jsonPath("$.lines[0].recommendations[0].inventoryTagCode").value(MATERIAL_ONE_FIFO_BOARD_CODE));
    }

    @Test
    void rejectsNonRecommendedPickUntilOperatorConfirms() throws Exception {
        mockMvc.perform(post("/api/outbound/pick-with-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryTagCode": "%s",
                                  "qty": 10,
                                  "outboundOrderId": %d,
                                  "outboundOrderLineId": %d,
                                  "confirmNonRecommended": false
                                }
                                """.formatted(MATERIAL_ONE_NEXT_BOARD_CODE, OUTBOUND_ORDER_ID, OUTBOUND_LINE_ONE_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("当前出库库存标签不在推荐出库方案中，是否继续按非推荐方案出库？"));

        mockMvc.perform(post("/api/outbound/pick-with-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryTagCode": "%s",
                                  "qty": 10,
                                  "outboundOrderId": %d,
                                  "outboundOrderLineId": %d,
                                  "confirmNonRecommended": true
                                }
                                """.formatted(MATERIAL_ONE_NEXT_BOARD_CODE, OUTBOUND_ORDER_ID, OUTBOUND_LINE_ONE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inventoryTagCode").value(MATERIAL_ONE_NEXT_BOARD_CODE));
    }

    @Test
    void partialPickPreservesRemainderAndLaterBoardViolatesFifo() throws Exception {
        mockMvc.perform(post("/api/outbound-orders/{id}/release-and-lock", OUTBOUND_ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseIds": [1]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/outbound/pick-with-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryTagCode": "%s",
                                  "qty": 30,
                                  "outboundOrderId": %s,
                                  "outboundOrderLineId": %s
                                }
                                """.formatted(MATERIAL_ONE_FIFO_BOARD_CODE, OUTBOUND_ORDER_ID, OUTBOUND_LINE_ONE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickedQty").value(30.0))
                .andExpect(jsonPath("$.newInventoryTagStatus").value("LOCKED"));

        mockMvc.perform(get("/api/outbound/inventory-tag-lookup")
                        .param("inventoryTagCode", MATERIAL_ONE_FIFO_BOARD_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickedQty").value(30.0))
                .andExpect(jsonPath("$.boardQty").value(100.0));

        mockMvc.perform(post("/api/outbound/pick-with-order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inventoryTagCode": "%s",
                                  "qty": 10,
                                  "outboundOrderId": %s,
                                  "outboundOrderLineId": %s
                                }
                                """.formatted(MATERIAL_ONE_NEXT_BOARD_CODE, OUTBOUND_ORDER_ID, OUTBOUND_LINE_ONE_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("FIFO 违规：请先出库更早入库的库存标签 " + MATERIAL_ONE_FIFO_BOARD_CODE));

        InventoryTag board = inventoryTagMapper.selectById(MATERIAL_ONE_FIFO_BOARD_ID);
        assertThat(board.getPickedQty()).isEqualByComparingTo("30.000");

        InventoryBalance balance = inventoryBalanceMapper.selectOne(
                Wrappers.<InventoryBalance>lambdaQuery()
                        .eq(InventoryBalance::getMaterialId, 1L)
                        .eq(InventoryBalance::getWarehouseId, 1L)
                        .eq(InventoryBalance::getStorageLocationId, 1L));
        assertThat(balance.getOnHandQty()).isEqualByComparingTo("370.000");
    }

    private void resetBoard(Long boardId) {
        InventoryTag board = inventoryTagMapper.selectById(boardId);
        board.setStatus("RECEIVED");
        board.setPickedQty(BigDecimal.ZERO);
        board.setLockedByOrderId(null);
        board.setLockedByOrderLineId(null);
        inventoryTagMapper.updateById(board);
    }
}
