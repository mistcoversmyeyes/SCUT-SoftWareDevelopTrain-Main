# Iteration 5 FR-09 AI 预警功能落地规格

## 1. 目标与边界

本规格承接 `docs/specs/2026-06-23-ai-warning-scope.md` 的 Week 4 方向冻结结论，将 FR-09 从“AI 预警方向与数据准备”推进到 Iteration 5 可演示的规则型分析功能。

本轮 AI 功能目标：

- 使用 WMS 内部库存余额和 `inventory_flow_history` 导入流水识别物料缺货风险。
- 使用最近入库、最近出库、库存账龄和近 30 天出库量识别呆滞风险。
- 使用导入流水中的 `quality_status` 和 `SCRAP` 记录识别质量/报废风险。
- 在 Web 端展示数据准备状态、风险摘要、行级风险和触发原因。
- 接入 OpenAI-compatible AI API，将规则型风险结果整理为仓储预警报告和处理建议。

本轮不做：

- 不训练真实机器学习模型。
- 不接入外部市场销售、新闻、天气、交通或突发事件数据。
- 不实现 AI 仓库管理员自动补货、自动调仓、自动任务下发。
- 不让大模型直接决定风险等级；风险等级仍由本地规则输出。
- 不改变核心库存事务表，不把导入样例写入正式库存流水。
- 不校准真实业务阈值；阈值仍为演示默认值。

## 2. 与 Week 4 方向规格的关系

`2026-06-23-ai-warning-scope.md` 保持为 Week 4 WP4-05 的方向规格事实源，继续回答：

- 四个 AI 候选方向如何取舍。
- 缺货、呆滞、报废/失效风险需要哪些字段。
- 规则型预警雏形如何定义。
- 哪些问题进入后续产品债。

本文件只回答 Iteration 5 如何落地：

- 具体接口是什么。
- 接口如何读取现有数据。
- 页面展示什么。
- 当前实现与 Week 4 建议口径有哪些差异或降级。

## 3. 功能入口

### 3.1 后端接口

```text
GET /api/ai-warning/analysis/inventory-risks
```

返回内容：

- `direction`：本轮 AI 预警方向说明。
- `readinessCode`：数据准备状态，取值 `NOT_READY`、`PARTIAL`、`READY`。
- `readinessLabel`：数据准备状态展示文案。
- `readinessReason`：数据准备状态解释。
- `snapshotDate`：分析基准日期。
- `generatedAt`：分析生成时间。
- `summary`：风险统计。
- `rows`：按物料、仓库、库位展开的风险结果。

```text
GET /api/ai-warning/analysis/inventory-risk-report
```

返回内容：

- `status`：报告状态，取值 `GENERATED`、`NOT_CONFIGURED`、`FAILED`。
- `configured`：是否已经配置 AI API Key。
- `provider`：当前为 `openai-compatible`。
- `model`：当前调用模型。
- `generatedAt`：报告生成时间。
- `summary`：本次报告所基于的规则型风险摘要。
- `reportMarkdown`：AI API 生成的中文 Markdown 建议报告。
- `message`：状态说明或错误提示。

### 3.2 前端入口

页面入口沿用：

```text
库存监控 -> AI 数据导入
/inventory/ai-import
```

页面展示：

- CSV 样例下载与导入。
- 导入批次与导入记录。
- AI 预警分析结果。
- AI 建议报告。
- 风险摘要：分析对象、缺货高风险、呆滞高风险、质量风险。
- 行级指标：物料、可用量、近 7 日均出库、库存覆盖天数。
- 风险标签：缺货风险、呆滞风险、质量风险。
- 触发原因：可解释文本，不展示为黑盒模型结论。
- “生成 AI 建议报告”按钮：调用后端 AI API 接口，未配置 API Key 时展示配置提示。

## 4. 数据输入

### 4.1 当前库存余额

分析接口复用库存查询已有口径：

```text
InventoryTransactionMapper.selectInventoryBalances(null, null, null)
```

用于取得：

- `materialCode`
- `materialName`
- `warehouseCode`
- `locationCode`
- `onHandQty`
- `availableQty`
- `outboundLockedQty`
- `manualLockedQty`
- `sealedQty`

设计意图：AI 预警页和库存查询页看到的数量口径一致，避免同一库存对象出现两套可用量解释。

### 4.2 导入流水

分析接口读取：

```text
ai_inventory_flow_history
```

由以下接口写入：

```text
POST /api/ai-warning/imports/inventory-flow-history
```

当前 CSV 模板字段：

```text
business_date,material_code,warehouse_code,location_code,board_code,movement_type,quantity,source_order_no,quality_status
```

兼容说明：

- Week 4 规格中建议字段名为 `inventory_tag_code`。
- 当前导入模板沿用历史实现字段 `board_code`，业务语义仍按库存标签/载体码理解。
- 若后续统一命名，需要同步修改导入模板、样例 CSV、解析器、页面列名和相关文档。

### 4.3 物料阈值

当前只读取：

- `material.low_stock_qty`

本轮未新增 `material_warning_profile` 或物料级 AI 阈值配置表。

## 5. 数据准备状态

| 状态 | 触发条件 | 页面解释 |
| --- | --- | --- |
| `NOT_READY` | 没有导入样例流水 | 尚未导入 `inventory_flow_history`，风险计算降级为数据未准备 |
| `PARTIAL` | 有样例流水但没有 `OUTBOUND` 记录 | 缺少出库流水，缺货/呆滞只能部分计算 |
| `READY` | 已有样例流水且包含 `OUTBOUND` 记录 | 已具备规则型预警所需的基础历史数据 |

## 6. 风险输出结构

每一行风险结果对应一个物料、仓库、库位库存对象。

核心字段：

- `materialCode`
- `materialName`
- `warehouseCode`
- `locationCode`
- `onHandQty`
- `availableQty`
- `lockedQty`
- `sealedQty`
- `avgDailyOutbound7d`
- `avgDailyOutbound30d`
- `daysOfCover`
- `lastInboundDate`
- `lastOutboundDate`
- `inventoryAgeDays`
- `daysSinceLastOutbound`
- `latestQualityStatus`
- `shortageRisk`
- `stagnationRisk`
- `qualityRisk`

风险对象结构：

```text
code, label, tone, reason
```

其中：

- `code`：规则结果编码。
- `label`：页面展示标签。
- `tone`：页面标签颜色语义。
- `reason`：触发原因，必须能回溯到数量、时间、阈值或状态字段。

## 7. 规则口径

### 7.1 缺货风险

默认阈值：

- `lead_time_days = 7`
- `safety_stock_days = 3`
- `replenishment_window = 10`

计算：

```text
avgDailyOutbound7d = outbound_qty_7d / 7
avgDailyOutbound30d = outbound_qty_30d / 30
dailyUse = max(avgDailyOutbound7d, avgDailyOutbound30d, 0)
daysOfCover = availableQty / dailyUse
```

规则：

| 条件 | 结果 |
| --- | --- |
| `availableQty <= 0` | `CRITICAL` |
| 无导入流水 | `DATA_UNPREPARED` |
| `daysOfCover <= replenishment_window` | `HIGH` |
| `availableQty <= material.low_stock_qty` | `WATCH` |
| 其他 | `NONE` |

### 7.2 呆滞风险

默认阈值：

- `stagnant_days_threshold = 45`

规则：

| 条件 | 结果 |
| --- | --- |
| `onHandQty <= 0` | `NONE` |
| 无导入流水 | `DATA_UNPREPARED` |
| 最近出库间隔达到 45 天且近 30 天出库量为 0 | `HIGH` |
| 库存账龄大于等于 30 天且近 30 天出库量低于账面库存的 30% | `WATCH` |
| 其他 | `NONE` |

### 7.3 质量/报废风险

规则：

| 条件 | 结果 |
| --- | --- |
| 存在 `SCRAP` 流水 | `EXPIRED` |
| 最新 `quality_status = EXPIRED` | `EXPIRED` |
| 最新 `quality_status = HOLD/NEAR_EXPIRY` | `HIGH` |
| 无导入流水 | `DATA_UNPREPARED` |
| 其他 | `NONE` |

说明：

- 当前质量/报废风险不是完整有效期模型。
- `production_date`、`expiry_date`、`shelf_life_days`、`batch_no` 尚未进入导入模板。
- 后续若要做真实近效期/过期判断，应先扩展导入模板与库存标签批次字段。

## 8. 验收标准

### 8.1 接口验收

- 未导入样例流水时，分析接口返回 `readinessCode=NOT_READY`。
- 导入包含 `OUTBOUND` 的样例流水后，分析接口返回 `readinessCode=READY`。
- 接口返回 `summary`，包含缺货、呆滞、质量风险统计。
- 接口返回 `rows`，每行至少包含三类风险对象和触发原因。
- 未配置 `WMS_AI_API_KEY` 时，报告接口返回 `status=NOT_CONFIGURED`，不影响规则型分析。
- 配置 `WMS_AI_API_KEY` 后，报告接口调用配置的大模型 API 并返回 `reportMarkdown`；OpenAI 默认使用 `/responses`，DeepSeek 默认使用 `/chat/completions`。

### 8.2 页面验收

- 页面可下载样例 CSV。
- 页面可导入样例 CSV，并显示导入摘要。
- 页面可展示 AI 预警分析摘要。
- 页面可展示缺货、呆滞、质量风险标签。
- 页面可触发 AI 建议报告生成。
- 页面在报告生成期间展示生成中状态、已等待时间和预计耗时提示。
- 页面生成报告后在“AI 建议报告”区域展示报告正文，并提供复制和下载 Markdown 操作。
- 页面支持在 Markdown 预览和源码视图之间切换，便于现场阅读和排查模型输出。
- 未配置 API Key 时，页面展示“未配置”提示而不是报错空白。
- AI API 调用失败时，页面展示可重试和查看后端日志的提示，而不是只显示空白失败态。
- 页面不把规则型预警描述为真实 AI 模型训练结果。

## 9. AI API 配置

后端配置项：

```text
wms.ai.api-key
wms.ai.base-url
wms.ai.model
wms.ai.api-format
```

对应环境变量：

```bash
WMS_AI_API_KEY
WMS_AI_BASE_URL
WMS_AI_MODEL
WMS_AI_API_FORMAT
```

默认值：

```text
WMS_AI_BASE_URL=https://api.openai.com/v1
WMS_AI_MODEL=gpt-4o-mini
WMS_AI_API_FORMAT=auto
```

`WMS_AI_API_FORMAT` 可选 `auto`、`responses`、`chat-completions`。`auto` 下，`WMS_AI_BASE_URL=https://api.deepseek.com` 使用 `/chat/completions`，其他默认使用 `/responses`。

安全要求：

- `WMS_AI_API_KEY` 不得写入仓库。
- 文档和日志不得记录真实 Key。
- 大模型只接收规则型风险摘要和高优先级风险行，不接收用户账号、token 或私有连接信息。

Prompt 边界：

- 要求模型输出中文 Markdown。
- 要求模型只根据输入的规则型风险结果生成报告。
- 要求模型不得声称已训练机器学习模型。
- 要求模型不得编造外部市场、天气、新闻或供应商实时状态。
- 要求报告包含优先级、建议负责人、完成时限、数据缺口和查看/流转建议，便于仓库主管、采购、质检协同处理。

报告查看方式：

- Web 页面路径：`/inventory/ai-import`。
- 点击“生成 AI 建议报告”后，页面会自动定位到“AI 建议报告”区域。
- 报告生成后可在页面内直接查看，也可点击“复制报告”或“下载 Markdown”进行流转。
- 报告只作为规则型风险结果的建议性解释，最终处置仍以人工复核和业务单据为准。

## 10. 样例验收

使用：

```text
frontend/public/samples/week4-inventory-flow-history-sample.csv
```

预期：

- CSV 导入成功行数为 21，失败行数为 0。
- 分析状态为 `READY`。
- 至少出现缺货高风险或紧急风险。
- 至少出现呆滞高风险。
- 至少出现质量/报废风险。

## 11. 已知限制与后续债务

- 当前规则阈值是演示默认值，未经过真实业务校准。
- 当前缺货风险未扣减未来未发出库单、未纳入在途采购。
- 当前呆滞风险按导入流水近似计算库存账龄，不等同批次级库龄。
- 当前质量/报废风险依赖 `quality_status` 和 `SCRAP`，不支持真实有效期计算。
- AI 建议报告依赖外部 API 可用性；接口失败时不会影响规则型风险分析。
- 当前 FR-09 若进入正式验收，仍建议补充 `docs/tests/acceptence-tests/` 下的独立验收步骤。

后续若确认 FR-08/FR-09 返工范围已关闭，可再更新 `docs/exec-plans/product-debt-tracker.md` 中 `PD-017` 的状态。
