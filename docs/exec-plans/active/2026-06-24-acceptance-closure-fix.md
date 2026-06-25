# Iteration 4 验收闭环修复执行计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Iteration 4 FR-02/FR-03 验收阻断，并统一入库单状态与库存标签领域命名，使 FR-01 至 FR-07 具备重新人工验收条件。

**Architecture:** 先修复出库创建和封存/FIFO 联动，确保业务行为可回归；再执行破坏式数据库和领域命名迁移；最后同步前端、测试和文档口径。数据库不保留生产数据兼容，直接以 `schema.sql` 与 `data.sql` 重建。

**Tech Stack:** Spring Boot + MyBatis-Plus + MySQL/H2 test profile；Vue 3 + Element Plus + Vitest；文档位于 `docs/specs/`、`docs/exec-plans/` 和 `docs/tests/acceptence-tests/`。

## Global Constraints

- 本分支工作目录：`/home/yuming/scut/SCUT_26_spring/software_develop_train/.worktrees/iter4-fix`。
- 规格事实源：`docs/specs/2026-06-24-acceptance-closure-fix-design.md`。
- 不返工 FR-08 表格批量导入。
- 不实现 FR-09 独立 AI 推荐或风险预警。
- 不由本分支代替人工验收；本分支只提供重新验收的功能条件和自动化验证证据。
- FR-03 只允许记录“当前已有手动封存/解封能力，但人工补验未完成”的事实；不得把未执行的普通出库/FIFO 联动补验写成通过。
- 入库单状态 `RELEASED / 已释放` 迁移为 `READY_TO_RECEIVE / 待收货`。
- 库存标签领域对象统一使用 `inventory_tag`、`InventoryTag`、`inventoryTagCode` 命名。
- 库存标签码统一使用 `IT:v1:` 前缀。
- 数据库采用破坏式 schema 重建，不提供历史兼容迁移脚本，不保留旧表旧列运行时兼容层。
- 后端变更运行 `cd backend && mvn test`。
- 前端逻辑变更运行 `cd frontend && npm test`。
- 前端路由、构建配置、依赖或样式主路径变更运行 `cd frontend && npm run build`。
- `docs/` 下新增、移动、删除文档时同步最近的 `index.md`。

---

## File Structure

### 后端出库与封存规则

- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderService.java`：出库单创建、修改、状态推进和响应组装。
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderRequest.java`：出库单请求字段和校验。
- `backend/src/main/java/com/scut/wms/outbound/OutboundOrderController.java`：出库单 HTTP 接口。
- `backend/src/main/java/com/scut/wms/lock/LockService.java`：释放并锁货、补锁、抢锁、强制审计。
- `backend/src/main/java/com/scut/wms/lock/InventoryHoldService.java`：封存、解封、手动锁库、手动解锁。
- `backend/src/main/java/com/scut/wms/outbound/picking/OutboundPickingService.java`：带单/不带单出库、FIFO 校验和扣减。
- `backend/src/main/resources/mapper/InventoryMapper.xml`：FIFO 候选查询、库存/标签追溯查询。
- `backend/src/main/resources/mapper/LockMapper.xml`：锁记录、封存/手锁记录、强制出库记录查询。
- `backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java`：FR-02/FR-03/FIFO/零头回归测试。

### 库存标签命名确认

- `backend/src/main/resources/schema.sql`：库存标签表、外键、索引和引用字段均使用 `inventory_tag` / `inventory_tag_id` / `inventory_tag_code`。
- `backend/src/main/resources/data.sql`：种子数据表名、字段名和码值前缀均使用库存标签命名。
- `backend/src/main/java/com/scut/wms/inbound/InventoryTag.java`。
- `backend/src/main/java/com/scut/wms/inbound/InventoryTagMapper.java`。
- 后端类统一引用 `InventoryTag`、`InventoryTagMapper`、`inventoryTagId`、`inventoryTagCode`。
- `frontend/src/api/inventoryTag.js`：库存标签 API wrapper。
- `frontend/src/views/inbound/InventoryTagDetailView.vue`、`InventoryTagPrintView.vue`、`frontend/src/views/inventory-tag/InventoryTagListView.vue`、`InventoryTagTraceView.vue`：库存标签页面和文案。
- `frontend/src/views/mobile/MobileInboundView.vue`、`MobileInventoryTagQueryView.vue`、`MobileOutboundView.vue`：字段、文案、码值名同步。

### 入库单待收货状态

- `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java`：状态常量、释放接口、状态转换。
- `backend/src/main/java/com/scut/wms/inbound/InboundOrderResponse.java`：响应状态。
- `backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java`：状态断言。
- `frontend/src/views/inbound/InboundOrderListView.vue`、`InboundDetailView.vue`、`InboundOrderFormView.vue`：状态字典和操作文案。
- `frontend/src/views/mobile/MobileInboundView.vue`：库存标签 `PRINTED` 与入库单 `READY_TO_RECEIVE` 展示边界。

### 文档

- `docs/specs/2026-06-10-inbound-core-design.md`
- `docs/specs/2026-06-15-outbound-master-data-inbound-enhancement-design.md`
- `docs/specs/2026-06-17-lock-goods-design.md`
- `docs/specs/2026-06-23-wms-completion-requirements.md`
- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md`
- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-results.md`
- `docs/exec-plans/product-debt-tracker.md`

---

## Parallelization Strategy

- **Wave 1 can run in parallel** because write scopes are mostly disjoint:
  - Task 1: 出库单创建 500 修复，主要改 `outbound/*` 和测试。
  - Task 2: 封存/FIFO 排除规则，主要改 `lock/*`、`picking/*`、`InventoryMapper.xml` 和测试。
- **Wave 2 should run after Wave 1 lands** because库存标签破坏式迁移会触碰几乎所有业务模块。
  - Task 3: 后端库存标签数据库/领域命名迁移。
  - Task 4: 前端库存标签页面/API/路由/文案迁移。
- **Wave 3 should run after Task 3/4 lands**:
  - Task 5: 入库单 `READY_TO_RECEIVE` 状态迁移。
  - Task 6: 文档和验收步骤口径同步。

每个 agent 必须在自己的 worktree 中工作，不能直接修改 `.worktrees/iter4-fix`。集成由主 agent 完成。

---

## Task 1: 修复出库单创建 500 与创建回归测试

**Files:**
- Modify: `backend/src/main/java/com/scut/wms/outbound/OutboundOrderService.java`
- Modify: `backend/src/main/java/com/scut/wms/outbound/OutboundOrderRequest.java`
- Modify if needed: `backend/src/main/java/com/scut/wms/outbound/OutboundOrderController.java`
- Test: `backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java`

**Interfaces:**
- Consumes: `POST /api/outbound-orders` with JSON payload `{ purpose, sourceDocNo, remark, lines: [{ supplierId, materialId, plannedQty, containerTypeId }] }`.
- Produces: create response containing `id`, `outboundNo`, `status = DRAFT`, one or more line displays, and no unhandled 500.

- [ ] **Step 1: Add a regression test for FR-02 create payload**

Add this test to `Week4BusinessRulesControllerTest`:

```java
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
            .andExpect(jsonPath("$.totalQty").value(1000.0))
            .andExpect(jsonPath("$.lines[0].containerTypeId").value(2));
}
```

- [ ] **Step 2: Run the focused failing/passing test**

Run:

```bash
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest#createsOutboundOrderWithContainerTypeFromAcceptancePayload test
```

Expected before fix: reproduce the 500 or expose the exact failing layer. Expected after fix: one test passes.

- [ ] **Step 3: Fix request/service mismatch**

Check these concrete failure points and patch the actual root cause:

- `OutboundOrderRequest.LineItem` must accept the payload fields sent by `OutboundOrderFormView.vue`.
- `OutboundOrderService.validateRequest()` must reject invalid supplier/material/container combinations with `BusinessException`, not an uncaught persistence exception.
- `OutboundOrderService.insertLines()` must set every non-nullable column defined by `outbound_order_line`.
- `OutboundOrderResponse.LineDisplay` must not dereference nullable warehouse/location/container values.

- [ ] **Step 4: Verify create and existing business rules**

Run:

```bash
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest test
```

Expected: all tests in `Week4BusinessRulesControllerTest` pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/scut/wms/outbound backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java
git commit -m "fix(outbound): 修复出库单创建阻断"
```

---

## Task 2: 补强封存库存的出库和 FIFO 排除规则

**Files:**
- Modify: `backend/src/main/java/com/scut/wms/lock/LockService.java`
- Modify: `backend/src/main/java/com/scut/wms/lock/InventoryHoldService.java`
- Modify: `backend/src/main/java/com/scut/wms/outbound/picking/OutboundPickingService.java`
- Modify: `backend/src/main/resources/mapper/InventoryMapper.xml`
- Test: `backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java`

**Interfaces:**
- Consumes: active hold rows in `inventory_hold` with `hold_type = SEAL` and `status = ACTIVE`.
- Produces: sealed inventory is excluded from automatic lock candidates and normal outbound picking.

- [ ] **Step 1: Strengthen tests for sealed inventory exclusion**

Ensure `Week4BusinessRulesControllerTest` covers both:

```java
@Test
void sealedInventoryTagIsExcludedFromAutoLockUntilUnsealed() throws Exception {
    // Existing test should remain and pass.
}
```

Add a normal-pick rejection case if missing:

```java
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
```

- [ ] **Step 2: Run the focused tests**

Run:

```bash
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest#sealedInventoryTagIsExcludedFromAutoLockUntilUnsealed,Week4BusinessRulesControllerTest#normalOutboundRejectsSealedInventoryTag test
```

Expected before fix: any missing exclusion fails. Expected after fix: both pass.

- [ ] **Step 3: Patch FIFO candidates and picking validation**

Concrete rules:

- `InventoryMapper.xml` FIFO candidate SQL must exclude inventory tags with active `inventory_hold` rows of `hold_type = 'SEAL'` or `hold_type = 'MANUAL_LOCK'`.
- `LockService.releaseAndLock()` and `reassignOrder()` must still check the current inventory tag status before locking.
- `OutboundPickingService` must reject normal outbound when the inventory tag status is `SEALED` or when an active seal hold exists.
- Forced/no-order behavior should keep its current audit semantics unless the code currently allows sealed stock to bypass the seal without explicit force wording; in that case return a readable `BusinessException`.

- [ ] **Step 4: Run backend regression**

Run:

```bash
cd backend && mvn -Dtest=Week4BusinessRulesControllerTest test
```

Expected: all Week4 business rule tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/scut/wms/lock backend/src/main/java/com/scut/wms/outbound/picking backend/src/main/resources/mapper/InventoryMapper.xml backend/src/test/java/com/scut/wms/outbound/Week4BusinessRulesControllerTest.java
git commit -m "fix(inventory): 排除封存库存出库候选"
```

---

## Task 3: 后端库存标签命名确认

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Modify: `backend/src/main/resources/data.sql`
- Modify: `backend/src/main/resources/mapper/InboundMapper.xml`
- Modify: `backend/src/main/resources/mapper/InventoryMapper.xml`
- Modify: `backend/src/main/resources/mapper/LockMapper.xml`
- Modify if needed: backend Java references to `InventoryTag`, `InventoryTagMapper`, `inventoryTagId`, `inventoryTagCode`.
- Test: all backend tests under `backend/src/test/java`.

**Interfaces:**
- Produces: physical table `inventory_tag`, field `inventory_tag_code`, Java entity `InventoryTag`, API fields `inventoryTagId` and `inventoryTagCode`.
- Removes: any production-facing non-inventory-tag naming for the inventory label object.

- [ ] **Step 1: Confirm entity and mapper names**

Class/interface declarations must use:

```java
public class InventoryTag { ... }
```

```java
public interface InventoryTagMapper extends BaseMapper<InventoryTag> { ... }
```

- [ ] **Step 2: Confirm schema and seed data**

In `schema.sql`:

- table name is `inventory_tag`.
- code field is `inventory_tag_code`.
- reference fields use `inventory_tag_id`.
- foreign keys reference `inventory_tag(id)`.
- index/constraint names use inventory tag wording.

In `data.sql`:

- inserts target `inventory_tag`.
- code columns use `inventory_tag_code`.
- demo codes use `IT:v1:`.

- [ ] **Step 3: Check backend references**

Use `rg` to verify current naming:

```bash
rg -n "IT:v1|inventory_tag|inventory_tag_code|InventoryTag|InventoryTagMapper|inventoryTagCode|inventoryTagId" backend/src
```

Matches should reflect current inventory-tag objects and fields, not legacy aliases.

- [ ] **Step 4: Update tests and expected values**

Update backend tests:

- imports use `InventoryTag`.
- mapper fields use `InventoryTagMapper`.
- test constants use `IT:v1:...`.
- JSON expectations use `inventoryTagCode`，本轮不保留旧字段兼容映射（包括 endpoint 兼容层）。

- [ ] **Step 5: Run backend tests**

Run:

```bash
cd backend && mvn test
```

Expected: all backend tests pass.

- [ ] **Step 6: Commit**

```bash
git add backend/src
git commit -m "refactor(inventory): 迁移库存标签后端命名"
```

---

## Task 4: 前端库存标签 API、路由和文案迁移

**Files:**
- Modify: `frontend/src/api/inventoryTag.js`
- Modify: `frontend/src/views/inventory-tag/InventoryTagListView.vue`
- Modify: `frontend/src/views/inventory-tag/InventoryTagTraceView.vue`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/menu.js`
- Modify: `frontend/src/views/inbound/InventoryTagDetailView.vue`
- Modify: `frontend/src/views/inbound/InventoryTagPrintView.vue`
- Modify: `frontend/src/views/mobile/MobileInboundView.vue`
- Modify: `frontend/src/views/mobile/MobileInventoryTagQueryView.vue`
- Modify: `frontend/src/views/mobile/MobileOutboundView.vue`
- Modify tests under `frontend/src/**/*.test.js`.

**Interfaces:**
- Consumes: backend API fields `inventoryTagId`, `inventoryTagCode`.
- Produces: UI with “库存标签/库存标签码” wording and consistent inventory-tag object naming.

- [ ] **Step 1: Check API wrapper**

```js
export async function fetchInventoryTagTrace(inventoryTagCode) {
  const response = await http.get(`/inventory-tags/${encodeURIComponent(inventoryTagCode)}/trace`)
  return response.data
}

export async function fetchInventoryTags(params) {
  const response = await http.get('/inventory/tags', { params })
  return response.data
}
```

Use the actual backend route names produced by Task 3.

- [ ] **Step 2: Check visible wording and route labels**

User-facing text should consistently use:

- 库存标签列表
- 库存标签详情
- 库存标签追溯
- 库存标签码
- 库存标签预览

Do not change unrelated business words such as 入库单、出库单、库存。

- [ ] **Step 3: Check field bindings**

Vue bindings and payload fields should use `inventoryTagCode`, `inventoryTagId`, and `inventoryTag` for tag data.

Tests and demo values should use `IT:v1:`.

- [ ] **Step 4: Run frontend tests and build**

Run:

```bash
cd frontend && npm test
cd frontend && npm run build
```

Expected: tests pass and production build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "refactor(frontend): 统一库存标签前端命名"
```

---

## Task 5: 迁移入库单 READY_TO_RECEIVE 状态

**Files:**
- Modify: `backend/src/main/java/com/scut/wms/inbound/InboundOrderService.java`
- Modify: `backend/src/main/java/com/scut/wms/inbound/InboundOrder.java`
- Modify: `backend/src/main/resources/data.sql`
- Test: `backend/src/test/java/com/scut/wms/inbound/InboundOrderControllerTest.java`
- Modify: `frontend/src/views/inbound/InboundOrderListView.vue`
- Modify: `frontend/src/views/inbound/InboundDetailView.vue`
- Modify: `frontend/src/views/mobile/MobileInboundView.vue`

**Interfaces:**
- Produces: inbound order status `READY_TO_RECEIVE`; inventory tag status remains `PRINTED`.
- Removes: inbound order status `RELEASED` and UI wording “已释放” for inbound orders.

- [ ] **Step 1: Update backend status constants and tests**

Replace inbound order `RELEASED` constants with `READY_TO_RECEIVE`.

In `InboundOrderControllerTest`, update assertions:

```java
.andExpect(jsonPath("$.order.status").value("READY_TO_RECEIVE"))
```

Inventory tag assertions stay:

```java
assertThat(tags).extracting(InventoryTag::getStatus).containsOnly("PRINTED");
```

- [ ] **Step 2: Update data and release behavior**

In `data.sql`, any inbound order previously in `RELEASED` state becomes `READY_TO_RECEIVE`.

`POST /api/inbound-orders/{id}/release` 需保持与入库语义一致；如进行端点重命名，沿用本轮入库状态语义迁移结果，不允许旧/新字段并行兼容。

- [ ] **Step 3: Update frontend status dictionaries**

Display:

- `READY_TO_RECEIVE` -> `待收货`
- `PRINTED` inventory tag -> `标签已生成` or `待入库`

Remove inbound-order-facing `已释放`.

- [ ] **Step 4: Run tests**

Run:

```bash
cd backend && mvn -Dtest=InboundOrderControllerTest test
cd frontend && npm test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/scut/wms/inbound backend/src/main/resources/data.sql backend/src/test/java/com/scut/wms/inbound frontend/src
git commit -m "refactor(inbound): 统一待收货状态命名"
```

---

## Task 6: 同步规格、产品债和验收步骤口径

**Files:**
- Modify: `docs/specs/2026-06-17-lock-goods-design.md`
- Modify: `docs/specs/2026-06-23-ai-data-import-template.md`
- Modify: `docs/specs/2026-06-23-wms-completion-requirements.md`
- Modify: `docs/specs/2026-06-24-acceptance-closure-fix-design.md`
- Modify: `docs/specs/index.md`
- Modify: `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md`
- Modify: `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-results.md`
- Modify: `docs/exec-plans/product-debt-tracker.md`

**Interfaces:**
- Consumes: final implementation naming from Tasks 3-5.
- Produces: current-target docs that consistently use `库存标签`、`库存标签码`、`IT:v1:`、`READY_TO_RECEIVE / 待收货`。

- [ ] **Step 1: Verify terminology in specs and acceptance steps**

Use a scoped residual-terminology scan before editing. The denylist should cover removed inventory-label terminology, removed table/field names, and removed demo-code prefixes.

Apply replacements only where the term referred to the inventory label object or where FR-08/FR-09 分类被写错：

- inbound order `RELEASED / 已释放` -> `READY_TO_RECEIVE / 待收货`
- `AI 数据导入` -> `表格导入`，除非是在说明当前页面旧入口名或历史文件名

Do not add compatibility notes for removed inventory-label terminology.

- [ ] **Step 2: Preserve acceptance-result authority**

Do not change failed/blocked/conditional conclusions to pass. It is acceptable to clarify that manual re-acceptance is pending. FR-08/FR-09 只能写“跳过/不可验收及原因”；FR-03 不得伪造人工补验结果。

- [ ] **Step 3: Update product debt statuses**

Update PD-015 / PD-016 to show they are covered by `fix/iter4`, retain the historical problem statement, and record the settled target wording:

- PD-015 -> `covered-by-fix/iter4`, decision `READY_TO_RECEIVE / 待收货`
- PD-016 -> `covered-by-fix/iter4`, decision `库存标签 / 库存标签码 / IT:v1:`

- [ ] **Step 4: Verify docs**

Run:

```bash
git diff --check -- docs
```

Expected: diff check exits with code 0.

- [ ] **Step 5: Commit**

```bash
git add docs/specs docs/tests/acceptence-tests docs/exec-plans/product-debt-tracker.md
git commit -m "docs(iter4): 同步库存标签和待收货语义"
```

---

## Final Integration Verification

- [ ] Run backend full suite:

```bash
cd backend && mvn test
```

- [ ] Run frontend tests:

```bash
cd frontend && npm test
```

- [ ] Run frontend build:

```bash
cd frontend && npm run build
```

- [ ] Check remaining terminology:

Run the scoped residual-terminology scan over `backend/src`、`frontend/src`、`docs/specs` and `docs/tests/acceptence-tests/iter4`.

Expected: no removed inventory-label terminology remains in active implementation or user-facing docs.

- [ ] Check git diff hygiene:

```bash
git diff --check
```

- [ ] Move this plan to completed after implementation:

```bash
git mv docs/exec-plans/active/2026-06-24-acceptance-closure-fix.md docs/exec-plans/completed/2026-06-24-acceptance-closure-fix.md
git commit -m "docs(exec-plans): 完成 iter4 验收闭环修复计划"
```
