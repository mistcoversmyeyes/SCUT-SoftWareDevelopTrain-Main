# Iteration 4 验收闭环修复设计

## 背景

本设计服务于 `fix/iter4` 分支。Iteration 4 人工验收显示，入库主路径已可用，但出库创建、出库锁货、封存与出库联动、库存标签术语和入库单状态命名仍会影响 FR-01 至 FR-07 的重新验收。

本分支采用验收闭环优先策略：先恢复出库和封存联动的可验收路径，再进行直接影响验收理解的领域命名迁移。FR-03 当前只确认已存在手动封存/解封能力；普通出库和自动 FIFO 锁货联动仍需在 FR-02 恢复后补验。FR-08 表格批量导入和 FR-09 AI 预警不在本分支返工范围内。

相关输入：

- `docs/specs/2026-06-23-wms-completion-requirements.md`
- `docs/specs/2026-06-17-lock-goods-design.md`
- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-results.md`
- `docs/exec-plans/product-debt-tracker.md`

## 目标

- 修复 FR-02 出库单创建时报“服务器内部错误，请查看日志”的阻断问题。
- 恢复出库单创建、释放锁货、带单出库、不带单出库、按件/零头、FIFO 和出库历史的可操作路径。
- 保留现有手动封存/解封能力，并补强封存库存对普通出库和自动 FIFO 锁货的排除规则。
- 将入库单状态 `RELEASED` 统一迁移为 `READY_TO_RECEIVE`，中文文案为“待收货”。
- 将领域对象统一标准化为“库存标签”，库存标签码统一使用 `IT:v1:` 前缀。
- 同步后端、前端、数据库物理命名、测试、种子数据、规格和验收测试文档口径。

## 不做范围

- 不返工 FR-08 表格批量导入。
- 不实现 FR-09 独立 AI 推荐或风险预警。
- 不接入真实 PDA、扫码枪、标签机、MES、ERP 或 SAP。
- 不做完整权限体系或真实机器学习模型。
- 不由本分支代替人工验收；本分支只提供重新验收的功能条件和自动化验证证据。

## 领域对象

### 入库单

入库单是业务收货计划，描述供应商、来源单号、物料明细、计划数量、目标仓库/库位和收货任务进度。

入库单状态机：

```text
DRAFT -> READY_TO_RECEIVE -> PARTIAL_RECEIVED -> COMPLETED
   \-> CANCELLED
```

状态口径：

- `DRAFT`：草稿，尚未生成库存标签。
- `READY_TO_RECEIVE`：待收货，库存标签已生成，仓库或手机端可开始收货。
- `PARTIAL_RECEIVED`：部分收货。
- `COMPLETED`：全部收货完成。
- `CANCELLED`：已取消。

`READY_TO_RECEIVE` 替代原 `RELEASED / 已释放`。该状态不表示“已打印”，也不表示“已入库”。

### 库存标签

库存标签是具体库存载体标识，描述一箱、一板或一批库存的标签码、所属入库单明细、数量、库位、生命周期和占用状态。

库存标签状态机：

```text
PRINTED -> RECEIVED -> LOCKED -> SHIPPED
              \-> SEALED -> RECEIVED
              \-> MANUAL_LOCKED -> RECEIVED
              \-> CANCELLED
```

状态口径：

- `PRINTED`：标签已生成，尚未入库。
- `RECEIVED`：已入库且可流转。
- `LOCKED`：被出库单锁定。
- `SEALED`：被人工封存。
- `MANUAL_LOCKED`：被人工手动锁库。
- `SHIPPED`：已出库。
- `CANCELLED`：已取消。

库存标签码格式：

```text
IT:v1:<inboundNo>:<lineNo>:<boxNo>
```

示例：

```text
IT:v1:IN-20260624-39A78DBC:1:1
```

## 数据库命名迁移

本项目当前没有生产数据，本分支采用破坏式 schema 重建，不提供历史兼容迁移脚本，旧表/旧列在重建后不保留运行时兼容读写层。

迁移要求（严格替换）：

- 使用 `inventory_tag` 作为库存标签主表，使用 `inventory_tag_code` 作为库存标签码字段。
- 所有外键、索引、mapper、实体、DTO、测试数据和种子数据同步使用库存标签命名。
- `data.sql` 中的库存标签码值统一使用 `IT:v1:` 前缀。
- 本地旧数据库如残留旧表，按现有数据库重置流程重新初始化。

## 后端设计

### 出库单创建

修复 FR-02 的首要路径是复现并消除创建出库单时的 500 错误。

排查和修复边界：

- `OutboundOrderRequest` 与前端 payload 字段一致。
- 出库明细的物料、供应商、容器类型、箱数和总件数字段映射正确。
- 服务层校验返回可读业务错误，不以未捕获异常形式返回 500。
- MyBatis mapper 与 `outbound_order`、`outbound_order_line` 字段一致。
- 创建成功后可继续释放并锁货。

### 出库和 FIFO

出库恢复后，需要保证：

- release-and-lock 按 FIFO 选择可用库存标签。
- 封存、手动锁库和已出库的库存标签不进入自动锁货候选。
- 普通带单出库只能消耗本单锁定的库存标签。
- 不带单出库走强制出库审计路径。
- 按件出库后保留剩余零头数量，零头可继续参与库存查询、追溯和后续出库。

### 封存与解封

现有手动封存/解封能力保留。需要补强：

- 封存库存标签后，当前库存的封存数量增加、可用数量减少。
- 解封后，封存数量回落、可用数量恢复。
- 封存操作记录可在锁库或占用记录页面查询。
- 自动锁货和普通出库均排除封存库存标签。
- 解封后库存标签重新进入可用候选。

### 命名迁移

后端 Java、Mapper、DTO 和 API 字段使用库存标签命名：

- 统一使用库存标签命名：`InventoryTag`、`inventoryTagId`、`inventoryTagCode`、`inventoryTags`。

如路由路径仍保留非库存标签命名入口，必须在本分支内同步迁移为库存标签语义路径，避免 API 与领域术语不一致。

## 前端设计

前端可见术语统一为库存标签：

- 库存标签列表、库存标签详情、库存标签追溯、库存标签码、库存标签预览

前端状态文案：

- 入库单 `READY_TO_RECEIVE` 显示为“待收货”。
- 库存标签 `PRINTED` 显示为“标签已生成”或“待入库”，不得显示为入库单状态。
- 库存标签 `RECEIVED` 显示为“已入库”。
- 库存标签 `SEALED` 显示为“已封存”。
- 库存标签 `MANUAL_LOCKED` 显示为“手动锁库”。

## 文档同步

需要同步更新：

- `docs/specs/2026-06-10-inbound-core-design.md`
- `docs/specs/2026-06-17-lock-goods-design.md`
- `docs/specs/2026-06-23-ai-data-import-template.md`
- `docs/specs/2026-06-23-wms-completion-requirements.md`
- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-test-steps.md`
- `docs/tests/acceptence-tests/iter4/week4-fr-acceptance-results.md`
- `docs/exec-plans/product-debt-tracker.md`

验收结果文档不得由本分支直接改为通过；只有人工复验完成后才能记录通过结论。截图、码值和入口名必须与当前实现口径一致，避免误导为旧路径仍可使用。

## 交付验证

后端验证：

- `mvn test`
- 覆盖出库单创建成功。
- 覆盖封存库存标签不参与 release-and-lock。
- 覆盖普通出库拒绝封存库存标签。
- 覆盖库存标签码 `IT:v1:` 生成和查询。
- 覆盖入库单 `READY_TO_RECEIVE` 状态。

前端验证：

- `npm test`
- 若涉及路由、构建、依赖或主样式，执行 `npm run build`。
- 检查主要页面不再出现库存标签以外的对象名。
- 检查入库单状态不再显示“已释放”。

文档验证：

- `docs/specs/index.md` 包含本规格。
- `docs/tests/index.md`、`docs/tests/acceptence-tests/index.md` 和 `docs/tests/acceptence-tests/iter4/index.md` 链接仍有效。
- 验收步骤中的对象名、状态名、截图引用和码值与实现一致。

## 建议提交顺序

1. 修复出库单创建和锁货主路径。
2. 补强封存库存的出库和 FIFO 排除规则。
3. 破坏式迁移库存标签领域命名和数据库物理命名。
4. 迁移入库单待收货状态命名。
5. 同步规格、产品债和验收步骤口径。

若实际排查发现 FR-02 根因与命名迁移强相关，可调整提交顺序，但每个提交仍应保持可验证。
