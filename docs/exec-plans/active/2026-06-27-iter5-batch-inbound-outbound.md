# Iteration 5 Batch Inbound/Outbound Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Iteration 5 batch inbound creation, batch outbound FIFO recommendation, mobile pending outbound processing, inventory tag batch printing, shortage filtering, and mobile seal/unseal workflows.

**Architecture:** Reuse existing WMS domain objects instead of adding new database tables. Batch inbound is a new batch create facade over existing `InboundOrder` + `InboundOrderLine` + `InventoryTag` behavior. Batch outbound adds FIFO recommendation as guidance, while actual picking remains scanner-driven through existing inventory movement and inventory tag update paths.

**Tech Stack:** Spring Boot 3.3.5, MyBatis-Plus, MySQL/H2 test profile, Vue 3 Composition API, Element Plus, Vitest, existing `html5-qrcode` mobile scanner component.

---

## Source Requirements

- Spec source: `docs/specs/2026-06-27-iter5-batch-inbound-outbound-requirements.md`
- Hard constraints: `FORCE_CONSTRAIN.md`
- Verification gates:
  - Backend behavior: `cd backend && mvn test`
  - Frontend logic: `cd frontend && npm test -- --run`
  - Frontend route or page changes: `cd frontend && npm run build`

## File Structure

### Backend

- Create `backend/src/main/java/com/scut/wms/inbound/BatchInboundOrderRequest.java` for batch inbound payload lines.
- Create `backend/src/main/java/com/scut/wms/inbound/BatchInboundOrderResponse.java` for grouped create results.
- Modify `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java` to group batch lines by supplier and call existing line insertion behavior.
- Modify `backend/src/main/java/com/scut/wms/inbound/InboundOrderController.java` to expose `POST /api/inbound-orders/batch`.
- Create `backend/src/main/java/com/scut/wms/outbound/picking/OutboundRecommendationLine.java` for per-line recommendation DTOs.
- Create `backend/src/main/java/com/scut/wms/outbound/picking/OutboundRecommendationResponse.java` for outbound order recommendation results.
- Create `backend/src/main/java/com/scut/wms/outbound/picking/OutboundRecommendationService.java` to compute FIFO recommendations without locking.
- Modify `backend/src/main/java/com/scut/wms/outbound/picking/ScanOutboundRequest.java` to add `Boolean confirmNonRecommended`.
- Modify `backend/src/main/java/com/scut/wms/outbound/picking/OutboundPickingService.java` so with-order picking no longer requires pre-locking in the Iteration 5 path.
- Modify `backend/src/main/java/com/scut/wms/outbound/picking/PickingController.java` to expose recommendation endpoints.
- Modify `backend/src/main/java/com/scut/wms/inventory/InventoryOverviewResponse.java` and `InventoryOverviewService.java` to expose `lowStockQty` and `shortage`.
- Test in `backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java`.
- Test in `backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java`.
- Test in new `backend/src/test/java/com/scut/wms/inventory/InventoryOverviewControllerTest.java`.

### Frontend

- Create `frontend/src/utils/batchInbound.js` for pending-line grouping and validation.
- Create `frontend/src/utils/batchInbound.test.js`.
- Modify `frontend/src/api/inbound.js` to add `batchCreateInboundOrders`.
- Create `frontend/src/views/inbound/BatchInboundCreateView.vue`.
- Modify `frontend/src/menu.js` to add `批量入库`.
- Modify `frontend/src/router/index.js` to register the batch inbound page.
- Modify `frontend/src/api/outbound.js` to add `fetchOutboundRecommendations` and pass `confirmNonRecommended`.
- Create `frontend/src/utils/outboundRecommendation.js`.
- Create `frontend/src/utils/outboundRecommendation.test.js`.
- Modify `frontend/src/views/outbound/OutboundOrderListView.vue` and `OutboundScanView.vue` to expose recommendation-driven picking.
- Modify `frontend/src/views/mobile/MobileOutboundView.vue` to show pending outbound orders and recommendation details.
- Modify `frontend/src/views/inbound/InventoryTagPrintView.vue` and `InventoryTagDetailView.vue` for print-selected and print-whole-order flows.
- Modify `frontend/src/views/inventory/InventoryOverviewView.vue` or `InventoryBalanceView.vue` for low-stock filtering; prefer `InventoryOverviewView.vue` for supplier-grouped shortage display.
- Create `frontend/src/views/mobile/MobileInventorySealView.vue`.
- Modify `frontend/src/views/mobile/MobileLayout.vue` and `frontend/src/router/index.js` to add mobile seal/unseal navigation.
- Test through `frontend/src/views/mobile/MobileRealScanViews.test.js`, `frontend/src/router/index.test.js`, and the new utility tests.

### Documentation

- Modify `docs/tests/acceptence-tests/index.md` if a new Iteration 5 acceptance folder is added.
- Create `docs/tests/acceptence-tests/iter5/index.md`.
- Create `docs/tests/acceptence-tests/iter5/iter5-fr-acceptance-test-steps.md`.
- Keep `docs/specs/2026-06-27-iter5-batch-inbound-outbound-requirements.md` unchanged unless implementation reveals a real spec correction.

---

## Task 1: Backend Batch Inbound Creation

**Files:**
- Create: `backend/src/main/java/com/scut/wms/inbound/BatchInboundOrderRequest.java`
- Create: `backend/src/main/java/com/scut/wms/inbound/BatchInboundOrderResponse.java`
- Modify: `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java`
- Modify: `backend/src/main/java/com/scut/wms/inbound/InboundOrderController.java`
- Test: `backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java`

- [ ] **Step 1: Write a failing backend test for grouping batch inbound lines by supplier**

Add this test to `InboundOrderControllerTest`:

```java
@Test
void batchCreateGroupsInboundOrdersBySupplierAndKeepsDuplicateMaterialLines() throws Exception {
    mockMvc.perform(post("/api/inbound-orders/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "sourceDocNo": "BATCH-IN-ITER5",
                              "remark": "iter5 batch inbound",
                              "lines": [
                                {
                                  "supplierId": 1,
                                  "materialId": 1,
                                  "containerTypeId": 1,
                                  "plannedQty": 230,
                                  "targetWarehouseId": 1,
                                  "targetLocationId": 1
                                },
                                {
                                  "supplierId": 1,
                                  "materialId": 1,
                                  "containerTypeId": 1,
                                  "plannedQty": 230,
                                  "targetWarehouseId": 1,
                                  "targetLocationId": 1
                                },
                                {
                                  "supplierId": 2,
                                  "materialId": 3,
                                  "containerTypeId": 2,
                                  "plannedQty": 100,
                                  "targetWarehouseId": 1,
                                  "targetLocationId": 2
                                }
                              ]
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderCount").value(2))
            .andExpect(jsonPath("$.orders[0].lines.length()").value(2))
            .andExpect(jsonPath("$.orders[0].lines[0].lineNo").value(1))
            .andExpect(jsonPath("$.orders[0].lines[1].lineNo").value(2))
            .andExpect(jsonPath("$.orders[1].lines.length()").value(1));
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
cd backend && mvn -Dtest=InboundOrderControllerTest#batchCreateGroupsInboundOrdersBySupplierAndKeepsDuplicateMaterialLines test
```

Expected: FAIL with `No handler found`, `404`, or missing `/api/inbound-orders/batch` endpoint.

- [ ] **Step 3: Add batch request and response records**

Create `BatchInboundOrderRequest.java`:

```java
package com.scut.wms.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchInboundOrderRequest(
        @Size(max = 64, message = "来源单号不能超过 64 个字符")
        String sourceDocNo,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        @Valid
        @NotEmpty(message = "批量入库明细不能为空")
        List<InboundOrderRequest.Line> lines
) {
}
```

Create `BatchInboundOrderResponse.java`:

```java
package com.scut.wms.inbound;

import java.util.List;

public record BatchInboundOrderResponse(
        int orderCount,
        int lineCount,
        List<InboundOrderResponse> orders
) {
}
```

- [ ] **Step 4: Implement grouped batch create in `InboundOrderService`**

Add this method to `InboundOrderService`:

```java
@Transactional
public BatchInboundOrderResponse createBatch(BatchInboundOrderRequest request) {
    InboundOrderRequest validationRequest = new InboundOrderRequest(
            request.sourceDocNo(),
            request.remark(),
            request.lines()
    );
    validateRequest(validationRequest);

    Map<Long, List<InboundOrderRequest.Line>> grouped = request.lines().stream()
            .collect(Collectors.groupingBy(
                    InboundOrderRequest.Line::supplierId,
                    LinkedHashMap::new,
                    Collectors.toList()
            ));

    List<InboundOrderResponse> orders = new ArrayList<>();
    for (List<InboundOrderRequest.Line> supplierLines : grouped.values()) {
        InboundOrderResponse created = create(new InboundOrderRequest(
                request.sourceDocNo(),
                request.remark(),
                supplierLines
        ));
        orders.add(created);
    }

    return new BatchInboundOrderResponse(
            orders.size(),
            request.lines().size(),
            orders
    );
}
```

Add imports:

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
```

- [ ] **Step 5: Add controller endpoint**

Add this method to `InboundOrderController`:

```java
@PostMapping("/batch")
public BatchInboundOrderResponse batchCreate(@Valid @RequestBody BatchInboundOrderRequest request) {
    return service.createBatch(request);
}
```

- [ ] **Step 6: Run the focused backend test and verify GREEN**

Run:

```bash
cd backend && mvn -Dtest=InboundOrderControllerTest#batchCreateGroupsInboundOrdersBySupplierAndKeepsDuplicateMaterialLines test
```

Expected: PASS, 1 test.

- [ ] **Step 7: Run inbound backend regression**

Run:

```bash
cd backend && mvn -Dtest=InboundOrderControllerTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/scut/wms/inbound backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java
git commit -m "feat(inbound): 支持批量创建入库单"
```

---

## Task 2: Frontend Batch Inbound Page

**Files:**
- Create: `frontend/src/utils/batchInbound.js`
- Create: `frontend/src/utils/batchInbound.test.js`
- Modify: `frontend/src/api/inbound.js`
- Create: `frontend/src/views/inbound/BatchInboundCreateView.vue`
- Modify: `frontend/src/menu.js`
- Modify: `frontend/src/router/index.js`
- Test: `frontend/src/router/index.test.js`

- [ ] **Step 1: Write utility tests for pending inbound lines**

Create `frontend/src/utils/batchInbound.test.js`:

```js
import { describe, expect, it } from 'vitest'
import { boxBreakdown, isCompleteBatchInboundLine, groupLinesBySupplier } from './batchInbound'

describe('batch inbound helpers', () => {
  it('computes full boxes and remainder from total quantity', () => {
    expect(boxBreakdown(230, 100)).toEqual({ boxCount: 2, remainder: 30 })
  })

  it('keeps duplicate supplier and material lines as separate records', () => {
    const lines = [
      { tempId: 'a', supplierId: 1, materialId: 1, plannedQty: 230 },
      { tempId: 'b', supplierId: 1, materialId: 1, plannedQty: 230 }
    ]
    expect(groupLinesBySupplier(lines).get(1)).toHaveLength(2)
  })

  it('requires warehouse and location before final submit', () => {
    expect(isCompleteBatchInboundLine({
      supplierId: 1,
      materialId: 1,
      containerTypeId: 1,
      plannedQty: 230,
      targetWarehouseId: 1,
      targetLocationId: 1
    })).toBe(true)

    expect(isCompleteBatchInboundLine({
      supplierId: 1,
      materialId: 1,
      containerTypeId: 1,
      plannedQty: 230,
      targetWarehouseId: 1
    })).toBe(false)
  })
})
```

- [ ] **Step 2: Run utility test and verify RED**

Run:

```bash
cd frontend && npm test -- src/utils/batchInbound.test.js
```

Expected: FAIL because `batchInbound.js` does not exist.

- [ ] **Step 3: Implement batch inbound helpers**

Create `frontend/src/utils/batchInbound.js`:

```js
export function boxBreakdown(totalQty, capacityQty) {
  const total = Number(totalQty) || 0
  const capacity = Number(capacityQty) || 0
  if (total <= 0 || capacity <= 0) {
    return { boxCount: 0, remainder: 0 }
  }
  return {
    boxCount: Math.floor(total / capacity),
    remainder: total % capacity
  }
}

export function isCompleteBatchInboundLine(line) {
  return Boolean(
    line?.supplierId &&
    line?.materialId &&
    line?.containerTypeId &&
    Number(line?.plannedQty) > 0 &&
    line?.targetWarehouseId &&
    line?.targetLocationId
  )
}

export function groupLinesBySupplier(lines) {
  return lines.reduce((groups, line) => {
    const supplierId = line.supplierId
    if (!groups.has(supplierId)) {
      groups.set(supplierId, [])
    }
    groups.get(supplierId).push(line)
    return groups
  }, new Map())
}
```

- [ ] **Step 4: Add batch inbound API wrapper**

Modify `frontend/src/api/inbound.js`:

```js
export async function batchCreateInboundOrders(payload) {
  const response = await http.post('/inbound-orders/batch', payload)
  return response.data
}
```

- [ ] **Step 5: Create `BatchInboundCreateView.vue`**

Create `frontend/src/views/inbound/BatchInboundCreateView.vue` with these sections:

```vue
<template>
  <section class="module-shell">
    <el-card>
      <template #header>
        <div class="header-row">
          <h2>批量入库创建</h2>
          <el-button type="primary" @click="openSupplierStep">添加入库明细</el-button>
        </div>
      </template>

      <el-steps :active="activeStep" finish-status="success" simple>
        <el-step title="选择供应商" />
        <el-step title="选择物料与数量" />
        <el-step title="设置库位" />
        <el-step title="提交创建" />
      </el-steps>

      <el-alert
        title="批量入库先构建待创建明细，所有明细设置库位后才会提交生成入库单。"
        type="info"
        show-icon
        :closable="false"
        class="section-gap"
      />

      <el-table :data="pendingLines" border stripe class="section-gap">
        <el-table-column prop="tempId" label="临时序号" width="100" />
        <el-table-column prop="supplierName" label="供应商" min-width="160" />
        <el-table-column prop="materialName" label="物料" min-width="180" />
        <el-table-column prop="plannedQty" label="总件数" width="100" align="right" />
        <el-table-column prop="boxCount" label="箱数" width="90" align="right" />
        <el-table-column prop="remainder" label="零头" width="90" align="right" />
        <el-table-column prop="locationName" label="目标库位" min-width="160" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button text type="danger" @click="removePendingLine(row.tempId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="submit-row">
        <el-button :disabled="!pendingLines.length" @click="openLocationStep">批量设置库位</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submitBatch">
          提交创建
        </el-button>
      </div>
    </el-card>
  </section>
</template>
```

Implement script with:

```js
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { batchCreateInboundOrders } from '../../api/inbound'
import { fetchMasterDataOptions, fetchMaterialContainerTypes } from '../../api/masterData'
import { boxBreakdown, isCompleteBatchInboundLine } from '../../utils/batchInbound'
```

Required behavior:

- `pendingLines` stores separate records with unique `tempId`.
- Selecting the same supplier/material/quantity twice pushes two separate records.
- `canSubmit` is `pendingLines.value.length > 0 && pendingLines.value.every(isCompleteBatchInboundLine)`.
- Submit payload maps pending lines to backend `lines` without `tempId`, display names, `boxCount`, or `remainder`.

- [ ] **Step 6: Register menu and route**

Modify `frontend/src/menu.js` under `入库管理` children:

```js
{ key: 'inbound-batch-create', title: '批量入库', path: '/inbound/batch-create',
  description: '按供应商批量选择物料，统一设置库位后批量创建入库单。', fields: ['供应商', '物料', '库位'] },
```

Modify `frontend/src/router/index.js`:

```js
import BatchInboundCreateView from '../views/inbound/BatchInboundCreateView.vue'
```

Add to `pageByKey`:

```js
'inbound-batch-create': BatchInboundCreateView,
```

- [ ] **Step 7: Run frontend tests and build**

Run:

```bash
cd frontend && npm test -- src/utils/batchInbound.test.js
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all tests pass; build completes with only existing Vite warnings.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/utils/batchInbound.js frontend/src/utils/batchInbound.test.js frontend/src/api/inbound.js frontend/src/views/inbound/BatchInboundCreateView.vue frontend/src/menu.js frontend/src/router/index.js
git commit -m "feat(frontend): 添加批量入库创建页面"
```

---

## Task 3: Backend FIFO Recommendation Without Pre-Lock

**Files:**
- Create: `backend/src/main/java/com/scut/wms/outbound/picking/OutboundRecommendationLine.java`
- Create: `backend/src/main/java/com/scut/wms/outbound/picking/OutboundRecommendationResponse.java`
- Create: `backend/src/main/java/com/scut/wms/outbound/picking/OutboundRecommendationService.java`
- Modify: `backend/src/main/java/com/scut/wms/outbound/picking/PickingController.java`
- Modify: `backend/src/main/java/com/scut/wms/outbound/picking/ScanOutboundRequest.java`
- Modify: `backend/src/main/java/com/scut/wms/outbound/picking/OutboundPickingService.java`
- Test: `backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java`

- [ ] **Step 1: Write failing tests for recommendation and confirmed non-recommended picking**

Add tests to `Week4BusinessRulesControllerTest`:

```java
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
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest#returnsFifoRecommendationForDraftOutboundOrderWithoutLockingInventory,Week4BusinessRulesControllerTest#rejectsNonRecommendedPickUntilOperatorConfirms test
```

Expected: FAIL because recommendation endpoint and `confirmNonRecommended` do not exist.

- [ ] **Step 3: Add recommendation DTOs**

Create `OutboundRecommendationLine.java`:

```java
package com.scut.wms.outbound.picking;

import java.math.BigDecimal;
import java.util.List;

public record OutboundRecommendationLine(
        Long outboundOrderLineId,
        Integer lineNo,
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal neededQty,
        List<PickRecommendation> recommendations
) {
}
```

Create `OutboundRecommendationResponse.java`:

```java
package com.scut.wms.outbound.picking;

import java.util.List;

public record OutboundRecommendationResponse(
        Long outboundOrderId,
        String outboundNo,
        List<OutboundRecommendationLine> lines
) {
}
```

- [ ] **Step 4: Implement `OutboundRecommendationService`**

Create `OutboundRecommendationService.java`:

```java
package com.scut.wms.outbound.picking;

import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import com.scut.wms.outbound.OutboundOrderMapper;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inventory.InventoryTransactionMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OutboundRecommendationService {
    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderLineMapper lineMapper;
    private final MaterialMapper materialMapper;
    private final InventoryTransactionMapper transactionMapper;

    public OutboundRecommendationService(
            OutboundOrderMapper orderMapper,
            OutboundOrderLineMapper lineMapper,
            MaterialMapper materialMapper,
            InventoryTransactionMapper transactionMapper
    ) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.materialMapper = materialMapper;
        this.transactionMapper = transactionMapper;
    }

    public OutboundRecommendationResponse recommend(Long outboundOrderId) {
        OutboundOrder order = orderMapper.selectById(outboundOrderId);
        if (order == null) {
            throw new BusinessException(org.springframework.http.HttpStatus.NOT_FOUND, "出库单不存在");
        }
        List<OutboundOrderLine> lines = lineMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<OutboundOrderLine>lambdaQuery()
                        .eq(OutboundOrderLine::getOutboundOrderId, outboundOrderId)
                        .orderByAsc(OutboundOrderLine::getLineNo)
        );
        return new OutboundRecommendationResponse(
                order.getId(),
                order.getOutboundNo(),
                lines.stream().map(this::recommendLine).toList()
        );
    }

    public boolean containsRecommendedTag(Long outboundOrderLineId, String inventoryTagCode) {
        OutboundOrderLine line = lineMapper.selectById(outboundOrderLineId);
        if (line == null) {
            throw new BusinessException("出库单明细行不存在");
        }
        return recommendationsFor(line).stream()
                .anyMatch(item -> item.inventoryTagCode().equals(inventoryTagCode));
    }

    private OutboundRecommendationLine recommendLine(OutboundOrderLine line) {
        Material material = materialMapper.selectById(line.getMaterialId());
        BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
        BigDecimal needed = line.getPlannedQty().subtract(picked);
        return new OutboundRecommendationLine(
                line.getId(),
                line.getLineNo(),
                line.getMaterialId(),
                material == null ? null : material.getMaterialCode(),
                material == null ? null : material.getMaterialName(),
                needed,
                recommendationsFor(line)
        );
    }

    private List<PickRecommendation> recommendationsFor(OutboundOrderLine line) {
        return transactionMapper.selectFifoRecommendations(
                line.getMaterialId(),
                List.of(line.getTargetWarehouseId())
        );
    }
}
```

- [ ] **Step 5: Wire recommendation endpoint**

Inject `OutboundRecommendationService` into `PickingController` or `OutboundOrderController`. Prefer `OutboundOrderController` because the endpoint is order-scoped.

Add constructor parameter:

```java
private final OutboundRecommendationService recommendationService;
```

Add endpoint:

```java
@GetMapping("/{id}/recommendations")
public OutboundRecommendationResponse recommendations(@PathVariable Long id) {
    return recommendationService.recommend(id);
}
```

- [ ] **Step 6: Add `confirmNonRecommended` to scan request**

Modify `ScanOutboundRequest.java`:

```java
public record ScanOutboundRequest(
        @NotBlank(message = "库存标签编码不能为空")
        String inventoryTagCode,
        BigDecimal qty,
        Long outboundOrderId,
        Long outboundOrderLineId,
        Boolean confirmNonRecommended
) {
    public boolean isConfirmNonRecommended() {
        return Boolean.TRUE.equals(confirmNonRecommended);
    }
}
```

- [ ] **Step 7: Modify with-order pick rule**

In `OutboundPickingService`, inject `OutboundRecommendationService`.

Replace the non-force branch in `pickWithOrder` with:

```java
if (!force) {
    if (request.outboundOrderId() == null || request.outboundOrderLineId() == null) {
        throw new BusinessException("带单出库必须指定出库单和明细行");
    }
    OutboundOrderLine line = outboundOrderLineMapper.selectById(request.outboundOrderLineId());
    if (line == null || !line.getOutboundOrderId().equals(request.outboundOrderId())) {
        throw new BusinessException("出库单明细行不存在");
    }
    if (!line.getMaterialId().equals(ctx.getMaterialId())) {
        throw new BusinessException("库存标签物料与出库明细不一致");
    }
    boolean recommended = recommendationService.containsRecommendedTag(
            request.outboundOrderLineId(),
            request.inventoryTagCode()
    );
    if (!recommended && !request.isConfirmNonRecommended()) {
        throw new BusinessException(
                org.springframework.http.HttpStatus.CONFLICT,
                "当前出库库存标签不在推荐出库方案中，是否继续按非推荐方案出库？"
        );
    }
}
```

Keep `inventoryHoldService.ensureOrderOutboundAllowed(board)` and quantity checks unchanged, so sealed/shipped/empty tags still fail.

- [ ] **Step 8: Run focused backend tests and regression**

Run:

```bash
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest#returnsFifoRecommendationForDraftOutboundOrderWithoutLockingInventory,Week4BusinessRulesControllerTest#rejectsNonRecommendedPickUntilOperatorConfirms test
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/scut/wms/outbound backend/src/main/java/com/scut/wms/outbound/picking backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java
git commit -m "feat(outbound): 添加FIFO推荐出库主流程"
```

---

## Task 4: Frontend Outbound Recommendation on Web and Mobile

**Files:**
- Modify: `frontend/src/api/outbound.js`
- Create: `frontend/src/utils/outboundRecommendation.js`
- Create: `frontend/src/utils/outboundRecommendation.test.js`
- Modify: `frontend/src/views/outbound/OutboundScanView.vue`
- Modify: `frontend/src/views/outbound/OutboundOrderListView.vue`
- Modify: `frontend/src/views/mobile/MobileOutboundView.vue`
- Test: `frontend/src/views/mobile/MobileRealScanViews.test.js`

- [ ] **Step 1: Write tests for recommendation membership**

Create `frontend/src/utils/outboundRecommendation.test.js`:

```js
import { describe, expect, it } from 'vitest'
import { isRecommendedInventoryTag, pendingOutboundStatuses } from './outboundRecommendation'

describe('outbound recommendation helpers', () => {
  const recommendation = {
    lines: [
      {
        outboundOrderLineId: 10,
        recommendations: [
          { inventoryTagCode: 'IT:v1:IN-1:1:1' },
          { inventoryTagCode: 'IT:v1:IN-1:1:2' }
        ]
      }
    ]
  }

  it('detects recommended tags per outbound line', () => {
    expect(isRecommendedInventoryTag(recommendation, 10, 'IT:v1:IN-1:1:1')).toBe(true)
    expect(isRecommendedInventoryTag(recommendation, 10, 'IT:v1:IN-2:1:1')).toBe(false)
  })

  it('keeps only pending mobile outbound statuses', () => {
    expect(pendingOutboundStatuses).toEqual(['DRAFT', 'RELEASED', 'PICKING', 'PARTIAL_SHIPPED'])
  })
})
```

- [ ] **Step 2: Implement helper and API**

Create `frontend/src/utils/outboundRecommendation.js`:

```js
export const pendingOutboundStatuses = ['DRAFT', 'RELEASED', 'PICKING', 'PARTIAL_SHIPPED']

export function isRecommendedInventoryTag(recommendation, outboundOrderLineId, inventoryTagCode) {
  const line = recommendation?.lines?.find((item) => item.outboundOrderLineId === outboundOrderLineId)
  return Boolean(line?.recommendations?.some((item) => item.inventoryTagCode === inventoryTagCode))
}
```

Modify `frontend/src/api/outbound.js`:

```js
export async function fetchOutboundRecommendations(id) {
  const response = await http.get(`/outbound-orders/${id}/recommendations`)
  return response.data
}
```

- [ ] **Step 3: Add web-side recommendation display and non-recommended confirm**

Modify `OutboundScanView.vue`:

- Load recommendation after an outbound order is loaded.
- Display each line's recommendations in a compact table.
- Before calling `pickWithOrder`, check `isRecommendedInventoryTag`.
- If not recommended, call `ElMessageBox.confirm` with:

```text
当前出库库存标签不在推荐出库方案中，是否继续按非推荐方案出库？
```

- If confirmed, call `pickWithOrder({ ..., confirmNonRecommended: true })`.
- If cancelled, do not call the API.

- [ ] **Step 4: Add mobile pending order list**

Modify `MobileOutboundView.vue`:

- On page load, call `fetchOutboundOrders({ status: pendingOutboundStatuses.join(',') })`.
- Render pending orders before scanner mode.
- Clicking an order sets active order and loads `fetchOutboundRecommendations(order.id)`.
- Hide completed and cancelled orders by relying on the status filter.
- In the scan submit path, use the same `ElMessageBox.confirm` non-recommended flow and send `confirmNonRecommended: true` only after confirmation.

- [ ] **Step 5: Run frontend tests and build**

Run:

```bash
cd frontend && npm test -- src/utils/outboundRecommendation.test.js
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all tests pass; build completes with only existing Vite warnings.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/api/outbound.js frontend/src/utils/outboundRecommendation.js frontend/src/utils/outboundRecommendation.test.js frontend/src/views/outbound/OutboundScanView.vue frontend/src/views/outbound/OutboundOrderListView.vue frontend/src/views/mobile/MobileOutboundView.vue
git commit -m "feat(frontend): 支持推荐方案出库确认"
```

---

## Task 5: Inventory Tag Batch Printing

**Files:**
- Modify: `frontend/src/views/inbound/InventoryTagPrintView.vue`
- Modify: `frontend/src/views/inbound/InventoryTagDetailView.vue`
- Modify: `frontend/src/views/inventory-tag/InventoryTagListView.vue`
- Modify: `frontend/src/router/index.js`
- Test: `frontend/src/router/index.test.js`

- [ ] **Step 1: Inspect current print page selection state**

Run:

```bash
rg -n "selected|批量打印|print|window.print|InventoryTagPrint" frontend/src/views/inbound frontend/src/views/inventory-tag
```

Expected: identify existing selection or add selection to `InventoryTagPrintView.vue`.

- [ ] **Step 2: Add print-selected support**

Modify `InventoryTagPrintView.vue`:

- Add `selectedRows = ref([])`.
- Add `<el-table-column type="selection" width="48" />`.
- Add button text `批量打印选中`.
- Add computed `printRows` that uses selected rows when selection is non-empty and all rows for order-wide print mode.

Core script:

```js
const selectedRows = ref([])
const printRows = computed(() => selectedRows.value.length ? selectedRows.value : tags.value)

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

function printSelected() {
  if (!printRows.value.length) {
    ElMessage.warning('请先选择库存标签')
    return
  }
  window.print()
}
```

- [ ] **Step 3: Add bordered independent label cards**

Ensure each card in the print area has:

```css
.label-card {
  border: 1px solid #111;
  break-inside: avoid;
  page-break-inside: avoid;
  padding: 10px;
  min-height: 160px;
}

@media print {
  .no-print {
    display: none !important;
  }
  .label-card {
    border: 1px solid #000;
  }
}
```

Each card displays inventory tag code, QR code, inbound order number, line number, supplier, material, quantity, location, container type, and sequence.

- [ ] **Step 4: Add order-wide print entry**

In `InventoryTagDetailView.vue`, add a button:

```vue
<el-button type="primary" @click="$router.push(`/inbound/${inboundOrderId}/inventory-tags/print`)">
  一键打印本单全部库存标签
</el-button>
```

Use the existing route `/inbound/:id/inventory-tags/print`.

- [ ] **Step 5: Run frontend verification**

Run:

```bash
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all tests pass; build completes.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/views/inbound/InventoryTagPrintView.vue frontend/src/views/inbound/InventoryTagDetailView.vue frontend/src/views/inventory-tag/InventoryTagListView.vue frontend/src/router/index.js
git commit -m "feat(frontend): 增强库存标签批量打印"
```

---

## Task 6: Inventory Shortage Display and Filtering

**Files:**
- Modify: `backend/src/main/java/com/scut/wms/inventory/InventoryOverviewResponse.java`
- Modify: `backend/src/main/java/com/scut/wms/inventory/InventoryOverviewService.java`
- Test: `backend/src/test/java/com/scut/wms/inventory/InventoryOverviewControllerTest.java`
- Modify: `frontend/src/views/inventory/InventoryOverviewView.vue`

- [ ] **Step 1: Write backend overview test**

Create `InventoryOverviewControllerTest.java`:

```java
package com.scut.wms.inventory;

import com.scut.wms.WmsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = WmsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryOverviewControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void overviewReturnsLowStockThresholdAndShortageFlag() throws Exception {
        mockMvc.perform(get("/api/inventory/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suppliers[0].materials[0].lowStockQty").exists())
                .andExpect(jsonPath("$.suppliers[0].materials[0].shortage").exists());
    }
}
```

- [ ] **Step 2: Add low-stock fields to response**

Modify `InventoryOverviewResponse.MaterialStock`:

```java
public record MaterialStock(
        Long id,
        String code,
        String name,
        BigDecimal lowStockQty,
        BigDecimal highStockQty,
        BigDecimal currentQty,
        boolean shortage
) {}
```

- [ ] **Step 3: Compute shortage from available/current quantity and low stock**

In `InventoryOverviewService.buildSupplierOverviews()`, pass low stock and shortage:

```java
BigDecimal low = mat.getLowStockQty();
boolean shortage = low != null && low.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(low) <= 0;
stocks.add(new InventoryOverviewResponse.MaterialStock(
        mat.getId(),
        mat.getMaterialCode(),
        mat.getMaterialName(),
        low,
        mat.getHighStockQty(),
        current,
        shortage
));
```

- [ ] **Step 4: Run backend test**

Run:

```bash
cd backend && mvn -Dtest=InventoryOverviewControllerTest test
```

Expected: PASS.

- [ ] **Step 5: Add frontend filters**

Modify `InventoryOverviewView.vue`:

- Add supplier select `filters.supplierId`.
- Add switch `filters.shortageOnly`.
- Render shortage suppliers/materials first.
- Use `mat.lowStockQty` for display.
- Use `mat.shortage` for "短缺" badge.

Core computed:

```js
const filters = reactive({ supplierId: '', shortageOnly: false })

const visibleSuppliers = computed(() => data.suppliers
  .filter((sup) => !filters.supplierId || sup.id === filters.supplierId)
  .map((sup) => ({
    ...sup,
    materials: sup.materials
      .filter((mat) => !filters.shortageOnly || mat.shortage)
      .sort((a, b) => Number(b.shortage) - Number(a.shortage))
  }))
  .filter((sup) => sup.materials.length > 0)
)
```

Use `visibleSuppliers` in the template instead of `data.suppliers`.

- [ ] **Step 6: Run full verification for this task**

Run:

```bash
cd backend && mvn -Dtest=InventoryOverviewControllerTest test
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all pass.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/scut/wms/inventory/InventoryOverviewResponse.java backend/src/main/java/com/scut/wms/inventory/InventoryOverviewService.java backend/src/test/java/com/scut/wms/inventory/InventoryOverviewControllerTest.java frontend/src/views/inventory/InventoryOverviewView.vue
git commit -m "feat(inventory): 展示短缺物料筛选"
```

---

## Task 7: Mobile Inventory Seal and Unseal

**Files:**
- Create: `frontend/src/views/mobile/MobileInventorySealView.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/views/mobile/MobileLayout.vue`
- Modify: `frontend/src/views/mobile/MobileRealScanViews.test.js`

- [ ] **Step 1: Add mobile seal route test**

Modify `MobileRealScanViews.test.js` to assert `/mobile/seal` route exists:

```js
it('registers mobile inventory seal route', () => {
  const route = router.resolve('/mobile/seal')
  expect(route.name).toBe('mobile-inventory-seal')
})
```

- [ ] **Step 2: Create mobile seal view**

Create `MobileInventorySealView.vue`:

```vue
<template>
  <section class="mobile-page">
    <h2>库存封存</h2>
    <MobileQrScanner
      reader-id="seal"
      label="扫描库存标签码"
      :disabled="loading"
      @decoded="handleDecoded"
    />

    <el-input v-model="inventoryTagCode" placeholder="库存标签码" clearable />
    <el-button type="primary" :loading="loading" @click="loadInventoryTag">查询</el-button>

    <el-card v-if="tagInfo" class="result-card">
      <p><strong>{{ tagInfo.inventoryTagCode }}</strong></p>
      <p>{{ tagInfo.materialCode }} {{ tagInfo.materialName }}</p>
      <p>{{ tagInfo.locationName }}</p>
      <p>状态：{{ tagInfo.inventoryTagStatus }}</p>

      <el-form v-if="canSeal" :model="sealForm" label-position="top">
        <el-form-item label="封存原因" required>
          <el-input v-model="sealForm.reason" placeholder="请输入封存原因" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="sealForm.remark" type="textarea" />
        </el-form-item>
        <el-button type="warning" :loading="submitting" @click="sealCurrent">封存</el-button>
      </el-form>

      <el-button v-if="canUnseal" type="success" :loading="submitting" @click="unsealCurrent">解封</el-button>
    </el-card>
  </section>
</template>
```

Script imports:

```js
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MobileQrScanner from '../../components/mobile/MobileQrScanner.vue'
import { lookupInventoryTag } from '../../api/outbound'
import { sealInventoryTag, unsealInventoryTag } from '../../api/inventoryTag'
```

Required logic:

- `handleDecoded(text)` sets `inventoryTagCode` and calls `loadInventoryTag`.
- `loadInventoryTag()` calls `lookupInventoryTag`.
- `canSeal` is true when status is `RECEIVED` and active hold is not `SEALED`.
- `canUnseal` is true when status is `SEALED` or active hold type is `SEALED`.
- `sealCurrent()` requires `sealForm.reason.trim()`.
- `unsealCurrent()` uses `ElMessageBox.confirm('确认解封该库存标签？', '解封确认')`.
- Operator value is current logged-in username from auth store, falling back to `mobile`.

- [ ] **Step 3: Register mobile route and nav**

Modify `frontend/src/router/index.js`:

```js
import MobileInventorySealView from '../views/mobile/MobileInventorySealView.vue'
```

Add child route under `/mobile`:

```js
{
  path: 'seal',
  name: 'mobile-inventory-seal',
  component: MobileInventorySealView,
  meta: { requiresAuth: true, title: '库存封存' }
}
```

Modify `MobileLayout.vue` to add a nav link labeled `库存封存` to `/mobile/seal`.

- [ ] **Step 4: Run frontend tests and build**

Run:

```bash
cd frontend && npm test -- src/views/mobile/MobileRealScanViews.test.js
cd frontend && npm test -- --run
cd frontend && npm run build
```

Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/mobile/MobileInventorySealView.vue frontend/src/router/index.js frontend/src/views/mobile/MobileLayout.vue frontend/src/views/mobile/MobileRealScanViews.test.js
git commit -m "feat(mobile): 支持扫码封存解封库存"
```

---

## Task 8: Acceptance Documentation

**Files:**
- Create: `docs/tests/acceptence-tests/iter5/index.md`
- Create: `docs/tests/acceptence-tests/iter5/iter5-fr-acceptance-test-steps.md`
- Modify: `docs/tests/acceptence-tests/index.md`

- [ ] **Step 1: Add Iteration 5 acceptance index**

Create `docs/tests/acceptence-tests/iter5/index.md`:

```markdown
# Iteration 5 Acceptance Tests

- `iter5-fr-acceptance-test-steps.md`：Iteration 5 批量入库、批量出库、手机待处理出库、库存标签批量打印、库存详情短缺筛选和手机端封存/解封验收步骤。
```

- [ ] **Step 2: Add acceptance steps**

Create `iter5-fr-acceptance-test-steps.md` with these sections:

```markdown
# Iteration 5 FR Acceptance Test Steps

## FR-01 批量入库创建

1. 进入 `入库管理 -> 批量入库`。
2. 添加供应商 A 的两条相同物料明细，数量相同。
3. 批量设置同一目标库位。
4. 添加供应商 B 的一条物料明细。
5. 提交创建。

预期：系统生成两张入库单；供应商 A 的入库单包含两条独立明细；重复物料不合并；释放后库存标签按明细行生成。

## FR-02 批量出库与 FIFO 推荐

1. 进入出库单页面创建多明细出库单。
2. 打开推荐方案。
3. 使用推荐库存标签扫码出库。
4. 使用不在推荐方案中的库存标签扫码。

预期：推荐标签直接出库；非推荐标签弹窗确认；确认后继续出库，取消则不出库。

## FR-03 手机端待处理出库单

1. 进入 `/mobile/outbound`。
2. 查看待处理出库单列表。
3. 点击一张待处理出库单。
4. 按推荐方案扫码出库。

预期：只显示待处理、出库中、部分出库单据；完成单据不再显示。

## FR-04 库存标签批量打印

1. 打开入库单库存标签打印页。
2. 勾选多个库存标签并点击批量打印选中。
3. 使用按入库单打印全部库存标签入口。

预期：每个库存标签都是独立带边框标签卡，包含二维码和关键信息。

## FR-05 库存详情短缺筛选

1. 在物料资料中设置低库存阈值。
2. 打开库存总览。
3. 开启只看短缺。
4. 按供应商筛选。

预期：短缺按可用数量与 `lowStockQty` 判断；AI 预警入口不参与本验收。

## FR-06 手机端库存封存/解封

1. 进入 `/mobile/seal`。
2. 扫描可用库存标签。
3. 填写封存原因并封存。
4. 再次扫描该库存标签并解封。

预期：封存原因必填，备注可选；解封只需确认；网页端库存标签状态同步变化。
```

- [ ] **Step 3: Update acceptance root index**

Modify `docs/tests/acceptence-tests/index.md`:

```markdown
- `iter5/`：Iteration 5 批量出入库与手机端封存验收步骤。
```

- [ ] **Step 4: Verify docs**

Run:

```bash
git diff --check -- docs/tests/acceptence-tests/index.md docs/tests/acceptence-tests/iter5
```

Expected: no output.

- [ ] **Step 5: Commit**

```bash
git add docs/tests/acceptence-tests/index.md docs/tests/acceptence-tests/iter5
git commit -m "docs(tests): 添加iter5验收步骤"
```

---

## Task 9: Full Integration Verification

**Files:**
- Verify all changed source and docs.

- [ ] **Step 1: Run backend full tests**

Run:

```bash
cd backend && mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run frontend full tests**

Run:

```bash
cd frontend && npm test -- --run
```

Expected: all frontend tests pass.

- [ ] **Step 3: Run frontend production build**

Run:

```bash
cd frontend && npm run build
```

Expected: build completes. Existing Vite chunk-size or PURE comment warnings may remain if unchanged.

- [ ] **Step 4: Run docs whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

- [ ] **Step 5: Commit verification notes if docs were updated**

If verification results are recorded in an Iteration 5 document, commit only that documentation:

```bash
git add docs/tests/acceptence-tests/iter5
git commit -m "docs(iter5): 记录集成验证结果"
```

If no documentation is changed by this verification step, do not create an empty commit.

---

## Plan Self-Review

- Spec coverage:
  - FR-01 batch inbound creation is covered by Tasks 1 and 2.
  - FR-02 batch outbound and FIFO recommendation is covered by Tasks 3 and 4.
  - FR-03 mobile pending outbound orders is covered by Task 4.
  - FR-04 web outbound recommendation confirmation is covered by Task 4.
  - FR-05 inventory tag batch printing is covered by Task 5.
  - FR-06 shortage display and supplier filtering is covered by Task 6.
  - FR-07 mobile seal/unseal is covered by Task 7.
  - Acceptance docs and verification are covered by Tasks 8 and 9.
- Scope control:
  - No task implements transfer packaging, split/merge package, automatic location allocation, complex FIFO authorization, or AI warning.
  - Existing `release-and-lock` code remains available but is not the Iteration 5 primary picking path.
- Verification:
  - Each implementation task has focused tests, full relevant test commands, and a commit step.
  - Final integration runs backend tests, frontend tests, frontend build, and docs whitespace checks.
