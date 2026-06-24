# Week 4 WP4-04 首期表格导入模板规格

> 文件路径沿用 `ai-data-import-template.md` 仅为历史兼容；本文当前对应 FR-08 表格批量导入，不把导入本身作为 AI 功能。

## 1. 首期导入对象冻结

WP4-04 首期固定导入对象为 `inventory_flow_history`。

选择原因：

1. `docs/specs/2026-06-23-ai-warning-scope.md` 已将 `inventory_flow_history` 定为单对象首选优先级。
2. 它能在不改库存余额和出库事务逻辑的前提下，为后续缺货/呆滞规则提供近 7/14/30/60 天的历史输入。
3. 它允许后续 WP4-03 直接读取独立样例流水，不依赖 WP4-01 正在并行修改的库存/出库事务实现。

本期不做：

- 不导入库存初始化余额。
- 不写入现有 `inventory_balance`、`inventory_movement`、`inventory_tag` 等核心事务表。
- 不承诺 Excel 解析；首期固定为 UTF-8 CSV。

## 2. 文件格式

- 文件类型：UTF-8 CSV
- 分隔符：英文逗号 `,`
- 第一行：固定表头
- 一行一个流水样例

固定表头：

```text
business_date,material_code,warehouse_code,location_code,board_code,movement_type,quantity,source_order_no,quality_status
```

字段口径：

| 字段 | 要求 | 说明 |
| --- | --- | --- |
| `business_date` | 必填，`yyyy-MM-dd` | 流水业务日期 |
| `material_code` | 必填 | 必须存在于当前物料主数据 |
| `warehouse_code` | 必填 | 必须存在于当前仓库主数据 |
| `location_code` | 必填 | 必须属于该仓库；作为项目本地扩展字段保留明细追溯 |
| `board_code` | 必填 | 样例库存标签码或载体标识 |
| `movement_type` | 必填 | 仅允许 `INBOUND`、`OUTBOUND`、`ADJUST`、`SEAL`、`UNSEAL`、`SCRAP` |
| `quantity` | 必填 | 大于 0，最小分析单位为件 |
| `source_order_no` | 必填 | 来源单号、封存单号或样例来源标识 |
| `quality_status` | 选填 | 如 `NORMAL`、`HOLD`、`NEAR_EXPIRY`、`EXPIRED` |

## 3. 校验规则

接口必须返回行级错误，不因单行失败阻断整批成功行落库。

首期校验包括：

1. 表头必须完全匹配固定模板。
2. 必填字段不能为空。
3. `business_date` 必须可解析为 `yyyy-MM-dd`。
4. `quantity` 必须为大于 0 的数字。
5. `material_code`、`warehouse_code` 必须能在现有主数据中找到。
6. `location_code` 必须属于 `warehouse_code`。
7. `movement_type` 必须属于允许集合。

## 4. 数据落点与查询

为避免影响现有库存事务口径，导入数据只写入独立导入样例表：

- `ai_import_batch`
- `ai_inventory_flow_history`

上述表名保留既有 `ai_` 前缀属于实现细节，不改变 FR-08 的验收分类。

查询接口：

- `POST /api/ai-warning/imports/inventory-flow-history`
- `GET /api/ai-warning/imports/inventory-flow-history/batches`
- `GET /api/ai-warning/imports/inventory-flow-history/records`

这些接口用于：

- 返回导入摘要和错误行。
- 让后续 WP4-03 读取批次和明细。
- 让课堂演示直接查看预警样例流水。

## 5. 样例文件

可直接导入的样例文件位于：

`frontend/public/samples/week4-inventory-flow-history-sample.csv`

覆盖场景：

- 正常库存
- 临近缺货
- 已缺货/极低库存
- 呆滞库存
- 高账龄低周转
- 封存导致可用不足
- `quality_status` 维度的近效期/过期字段准备
