# Week 4 WP4-05 AI 预警方向规格

## 1. 目标与边界

本规格用于冻结 Week 4 的 AI 预警方向、字段口径和样例数据准备要求，作为 WP4-04 批量导入、WP4-03 监控展示和后续模型化分析的共同事实源。

Week 4 的交付边界是“规则型预警雏形 + 数据准备口径”，不是已训练完成的 AI 模型。所有预警结论都必须可解释、可回溯到字段和规则，不得包装成外部数据驱动或机器学习已上线能力。

本规格只覆盖 WMS 内部数据驱动的以下近期目标：

- 缺货风险识别。
- 呆滞风险识别。
- 报废/失效风险识别的数据准备与规则雏形。

本规格不覆盖：

- 外部市场销售情况 AI 预警。
- 外部突发事件 AI 预警。
- AI 仓库管理员自动决策或自动执行。
- 真实模型训练、特征工程平台、在线推理服务。

## 2. Week 4 方向取舍

### 2.1 候选方向与结论

| 方向 | Week 4 结论 | 原因 |
| --- | --- | --- |
| 内部需求预测 AI 波动监控 | 部分吸收后收敛为“内部数据驱动的缺货风险识别” | 当前项目已经具备库存、入库、出库、看板、监控、导入等内部数据基础，能够在不引入外部依赖的前提下做可解释预警。Week 4 不做复杂需求预测模型，只保留基于历史出库和库存覆盖天数的规则型近似。 |
| 外部市场销售情况 AI 预警 | 本周不进入实现 | 需要外部销售订单、渠道销量、市场价格或客户需求数据，当前课程边界和系统数据都未提供稳定来源；如果强行纳入，会把 Week 4 扩展到跨系统集成和数据治理。 |
| 外部突发事件 AI 预警 | 本周不进入实现 | 需要新闻、天气、政策、交通、供应中断等事件源，以及事件到物料/供应商/仓库的映射关系，超出本周 WMS 演示范围。 |
| AI 仓库管理员 | 本周不进入实现 | 该方向要求系统具备更完整的任务编排、策略解释、异常处置、人工确认和权限边界，不适合在 Week 4 以“提示型预警准备”为目标的阶段引入。 |

### 2.2 本周主方向

Week 4 冻结的主方向是：

1. 基于 WMS 内部库存和历史出入库数据识别未来缺货风险。
2. 基于库存滞留时间、近期开销和有效期字段识别呆滞风险与报废/失效风险。

### 2.3 取舍原则

- 数据可得：优先使用现有 WMS 数据和 WP4-04 可导入数据。
- 结果可解释：预警结论必须能落到数量、时间、阈值、最近流转记录等字段。
- 课堂可演示：样例数据导入后应能稳定展示“正常/预警/高风险”对比结果。
- 范围可控：不把 Week 4 扩展成外部数据接入、模型训练或自动调度项目。

## 3. 风险定义

### 3.1 缺货风险

缺货风险用于回答“当前可用库存按照近期消耗速度是否会在补货准备周期内耗尽”。

建议输出等级：

- `NONE`：无明显缺货风险。
- `WATCH`：库存覆盖天数偏低，需关注。
- `HIGH`：预计将在预设补货周期内缺货。
- `CRITICAL`：当前已缺货或近似缺货。

### 3.2 呆滞风险

呆滞风险用于回答“库存是否长期未发生有效消耗，且仍占用库存空间或资金”。

建议输出等级：

- `NONE`
- `WATCH`
- `HIGH`

### 3.3 报废/失效风险

报废/失效风险用于回答“具有有效期、保质期或质量状态要求的库存，是否接近失效、过期或需报废”。

Week 4 只要求准备字段和规则雏形；如果首批样例数据暂时没有有效期字段，可先不在页面上展示，但 WP4-04 的模板应预留对应字段。

建议输出等级：

- `NONE`
- `WATCH`
- `HIGH`
- `EXPIRED`

## 4. 数据字段清单

### 4.1 字段分层约定

- `现有数据`：按现有 WMS 能力与 Week 4 总体规格，近期可直接复用或可从当前业务记录中整理得到的数据。
- `WP4-04 导入需准备`：Week 4 样例导入必须额外提供，才能稳定支撑预警计算和课堂演示的数据。
- `后续扩展数据`：本周先不强制准备，但后续若进入更真实的 AI 或更高精度规则，需要补充的数据。

### 4.2 缺货风险字段

| 字段 | 说明 | 层级 | 用途 |
| --- | --- | --- | --- |
| `material_code` | 物料编码 | 现有数据 | 预警聚合主键 |
| `material_name` | 物料名称 | 现有数据 | 展示和筛选 |
| `supplier_code` | 供应商编码 | 现有数据 | 展示、后续供应侧分析 |
| `warehouse_code` | 仓库编码 | 现有数据 | 按仓筛选 |
| `location_code` | 库位编码 | 现有数据 | 明细追溯 |
| `board_code` | 看板码 | 现有数据 | 追溯到箱/板级库存 |
| `available_qty` | 当前可用数量 | 现有数据 | 缺货主判断量 |
| `locked_qty` | 当前锁定数量 | 现有数据 | 解释为何“总量有货但不可用” |
| `sealed_qty` | 当前封存数量 | 现有数据 | 排除不可出库数量 |
| `last_inbound_at` | 最近入库时间 | 现有数据 | 判断补货节奏 |
| `last_outbound_at` | 最近出库时间 | 现有数据 | 判断消耗活跃度 |
| `outbound_qty_7d` | 最近 7 天出库数量 | WP4-04 导入需准备 | 计算短周期消耗速度 |
| `outbound_qty_14d` | 最近 14 天出库数量 | WP4-04 导入需准备 | 平滑异常波动 |
| `outbound_qty_30d` | 最近 30 天出库数量 | WP4-04 导入需准备 | 演示中长期消耗趋势 |
| `avg_daily_outbound_7d` | 最近 7 天日均出库量 | WP4-04 导入需准备 | 库存覆盖天数计算 |
| `avg_daily_outbound_30d` | 最近 30 天日均出库量 | WP4-04 导入需准备 | 缺货风险兜底 |
| `min_stock_level` | 最低库存阈值 | WP4-04 导入需准备 | 缺货规则兜底阈值 |
| `safety_stock_days` | 安全库存天数 | WP4-04 导入需准备 | 覆盖天数判断 |
| `lead_time_days` | 补货准备天数或默认采购提前期 | WP4-04 导入需准备 | 缺货风险主阈值 |
| `onway_inbound_qty` | 在途/待入库数量 | 后续扩展数据 | 降低误报 |
| `open_outbound_order_qty` | 已承诺未发货数量 | 后续扩展数据 | 更准确评估未来缺口 |

### 4.3 呆滞风险字段

| 字段 | 说明 | 层级 | 用途 |
| --- | --- | --- | --- |
| `material_code` | 物料编码 | 现有数据 | 聚合主键 |
| `warehouse_code` | 仓库编码 | 现有数据 | 按仓筛选 |
| `board_code` | 看板码 | 现有数据 | 追溯到具体库存载体 |
| `available_qty` | 当前可用数量 | 现有数据 | 仅对正库存计算呆滞 |
| `inventory_age_days` | 当前库存账龄（按入库或最近形成库存的时间计算） | WP4-04 导入需准备 | 呆滞判断主字段 |
| `days_since_last_outbound` | 距最近一次出库的天数 | WP4-04 导入需准备 | 识别长期未动销 |
| `outbound_qty_30d` | 最近 30 天出库数量 | WP4-04 导入需准备 | 判断近期开销 |
| `outbound_qty_60d` | 最近 60 天出库数量 | WP4-04 导入需准备 | 降低短期波动误判 |
| `turnover_days_estimate` | 周转天数估算 | WP4-04 导入需准备 | 解释性指标 |
| `stagnant_days_threshold` | 呆滞天数阈值 | WP4-04 导入需准备 | 物料可配置阈值 |
| `material_status` | 物料状态，如启用/停用/冻结 | 现有数据 | 排除已停用物料的误判 |
| `container_type` | 容器/包装类型 | 后续扩展数据 | 判断零头或大包装呆滞特征 |
| `material_category` | 物料类别 | 后续扩展数据 | 按类别差异化阈值 |

### 4.4 报废/失效风险字段

| 字段 | 说明 | 层级 | 用途 |
| --- | --- | --- | --- |
| `material_code` | 物料编码 | 现有数据 | 聚合主键 |
| `snapshot_date` | 风险计算基准日期 | WP4-04 导入需准备 | 与到期日比较 |
| `board_code` | 看板码 | 现有数据 | 追溯具体库存 |
| `available_qty` | 当前可用数量 | 现有数据 | 仅对正库存计算 |
| `received_at` | 入库时间 | 现有数据 | 与有效期计算配套 |
| `production_date` | 生产日期 | WP4-04 导入需准备 | 有效期计算基础 |
| `expiry_date` | 失效/到期日期 | WP4-04 导入需准备 | 报废风险主判断量 |
| `shelf_life_days` | 保质期天数 | WP4-04 导入需准备 | 无明确到期日时兜底 |
| `quality_status` | 质量状态，如正常/待检/冻结/近效期 | WP4-04 导入需准备 | 解释和筛选 |
| `scrap_warning_days` | 距到期多少天进入预警 | WP4-04 导入需准备 | 可配置阈值 |
| `batch_no` | 批次号 | 后续扩展数据 | 更真实的质量追溯 |
| `scrap_reason_code` | 报废原因编码 | 后续扩展数据 | 训练标签和复盘 |
| `quality_inspection_result` | 质检结果 | 后续扩展数据 | 区分近效期与质量不合格 |

### 4.5 Week 4 最小可用字段集

如果 WP4-04 只能支撑一版最小导入集，至少应保证以下字段可用：

- 物料主键与展示：`material_code`、`material_name`、`supplier_code`
- 库存现状：`available_qty`、`locked_qty`、`sealed_qty`、`warehouse_code`、`location_code`
- 时间字段：`last_inbound_at`、`last_outbound_at`
- 缺货规则字段：`avg_daily_outbound_7d`、`min_stock_level`、`safety_stock_days`、`lead_time_days`
- 呆滞规则字段：`inventory_age_days`、`days_since_last_outbound`、`outbound_qty_30d`、`stagnant_days_threshold`

若希望同一批样例数据也能覆盖报废/失效风险演示，再额外准备：

- `expiry_date` 或 `production_date + shelf_life_days`
- `quality_status`
- `scrap_warning_days`

## 5. 规则型预警雏形

### 5.1 总体原则

- 规则必须可解释，页面或日志中应能展示触发原因。
- 阈值应支持全局默认值与物料级覆盖，不在 Week 4 写死为单一常量。
- 当字段缺失时，规则应降级，而不是伪装成“AI 自动预测成功”。

### 5.2 缺货风险规则

建议先按物料维度聚合，再按仓库或库位展开明细。

核心指标：

- `daily_use = max(avg_daily_outbound_7d, avg_daily_outbound_30d, 0)`
- `days_of_cover = available_qty / daily_use`，当 `daily_use <= 0` 时视为不可用
- `replenishment_window = lead_time_days + safety_stock_days`

推荐默认规则：

1. `available_qty <= 0` -> `CRITICAL`
2. `available_qty > 0` 且 `daily_use > 0` 且 `days_of_cover <= replenishment_window` -> `HIGH`
3. `available_qty > 0` 且 `available_qty <= min_stock_level` -> `WATCH`
4. `daily_use = 0` 且 `available_qty > min_stock_level` -> `NONE`
5. `daily_use` 缺失时，降级为仅按 `available_qty <= min_stock_level` 判断 `WATCH`

建议默认阈值：

- `lead_time_days` 默认 7 天。
- `safety_stock_days` 默认 3 天。
- `min_stock_level` 默认由导入样例按物料给定，不建议统一常数。

展示解释建议：

- “当前可用 42，近 7 天日均出库 8，库存覆盖约 5.3 天，低于补货窗口 10 天。”

### 5.3 呆滞风险规则

核心指标：

- `days_since_last_outbound`
- `inventory_age_days`
- `outbound_qty_30d`

推荐默认规则：

1. `available_qty <= 0` -> `NONE`
2. `days_since_last_outbound >= stagnant_days_threshold` 且 `outbound_qty_30d = 0` -> `HIGH`
3. `inventory_age_days >= 30` 且 `outbound_qty_30d > 0` 且 `outbound_qty_30d < available_qty * 0.3` -> `WATCH`
4. `inventory_age_days < 30` 或近 30 天有稳定出库 -> `NONE`

推荐默认阈值：

- `stagnant_days_threshold` 默认 45 天。
- 如无物料级阈值，可按类别分层，Week 4 先统一全局默认值。

展示解释建议：

- “该物料库存账龄 62 天，最近 34 天无出库，超过呆滞阈值 45 天。”

### 5.4 报废/失效风险规则

核心指标：

- `days_to_expiry = expiry_date - snapshot_date`

推荐默认规则：

1. `expiry_date` 已过 -> `EXPIRED`
2. `days_to_expiry <= scrap_warning_days` -> `HIGH`
3. `days_to_expiry <= 30` 且大于 `scrap_warning_days` -> `WATCH`
4. 无有效期字段 -> 本周不计算该风险，只保留字段缺失标记

推荐默认阈值：

- `scrap_warning_days` 默认 7 天。
- `WATCH` 默认 30 天。

### 5.5 可配置性要求

Week 4 只要求把以下阈值视为“可配置字段”而非硬编码事实：

- `min_stock_level`
- `lead_time_days`
- `safety_stock_days`
- `stagnant_days_threshold`
- `scrap_warning_days`

配置优先级建议：

1. 物料级字段值
2. 物料类别默认值
3. 系统全局默认值

阈值校准不在本周完成，必须记录为后续产品债。

## 6. 与 WP4-04 导入联动要求

### 6.1 推荐导入模板结构

WP4-04 应优先准备能支撑本规格的样例模板。推荐至少三类表：

| Sheet/对象 | 作用 | 必填核心字段 |
| --- | --- | --- |
| `material_warning_profile` | 维护物料基础属性和预警阈值 | `material_code`、`material_name`、`supplier_code`、`min_stock_level`、`lead_time_days`、`safety_stock_days`、`stagnant_days_threshold`、可选 `shelf_life_days`、`scrap_warning_days` |
| `inventory_snapshot` | 给出当前库存快照 | `snapshot_date`、`material_code`、`warehouse_code`、`location_code`、`board_code`、`available_qty`、`locked_qty`、`sealed_qty`、`received_at` |
| `inventory_flow_history` | 给出用于计算近 7/14/30/60 天规则指标的流水 | `business_date`、`material_code`、`warehouse_code`、`board_code`、`movement_type`、`quantity`、`source_order_no`、可选 `quality_status` |

如果 WP4-04 首期只能支持一个导入对象，优先级建议如下：

1. `inventory_flow_history`
2. `material_warning_profile`
3. `inventory_snapshot`

原因：没有历史流水，缺货和呆滞规则只能退化成静态阈值判断；而有了历史流水后，即使库存快照先用种子数据或现有库存，也能形成更有说服力的预警演示。

### 6.2 样例数据覆盖要求

WP4-04 准备的样例数据至少要覆盖以下场景：

| 场景 | 必须出现的字段特征 | 预期风险结果 |
| --- | --- | --- |
| 正常库存 | `available_qty` 充足，近 7/30 天稳定出库 | 缺货 `NONE`，呆滞 `NONE` |
| 临近缺货 | `available_qty` 偏低，近 7 天有稳定出库，`days_of_cover <= replenishment_window` | 缺货 `HIGH` |
