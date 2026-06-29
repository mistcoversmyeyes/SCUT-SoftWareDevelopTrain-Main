# Iteration 5 FR-09 AI 预警返工与落地执行计划

## 目标

将 Week 4 已冻结的 AI 预警方向从“方向确认与数据准备”推进到 Iteration 5 可演示的规则型分析能力。

本轮主方向保持为：基于 WMS 内部库存余额和导入的 `inventory_flow_history` 历史流水，识别物料未来缺货、呆滞和质量/报废风险。

本轮不改变 Week 4 的方向取舍：

- 不接入外部市场销售数据。
- 不接入外部突发事件数据。
- 不实现 AI 仓库管理员自动决策或自动执行。
- 不训练真实机器学习模型。

## 完成记录

- 已完成 `GET /api/ai-warning/analysis/inventory-risks` 规则型分析接口。
- 已复用 `POST /api/ai-warning/imports/inventory-flow-history` CSV 导入结果作为分析输入。
- 已在 Web 端 `库存监控 -> AI 数据导入` 页面展示后端分析结果、风险摘要、覆盖天数和触发原因。
- 已接入 OpenAI-compatible AI API，用于把规则型风险结果生成中文仓储预警报告和处理建议。
- 已新增 `docs/specs/2026-06-29-ai-warning-scope.md` 作为 AI 功能落地规格。

验证记录：

- `cd backend && mvn -DskipTests compile`：通过。
- `cd backend && mvn -Dtest=InventoryFlowHistoryImportControllerTest test`：通过。
- `cd frontend && npm test`：10 个测试文件、46 个测试通过。
- `cd frontend && npm run build`：通过，仍有既有 Vite/Rollup PURE 注释和 chunk size warning。
- 样例导入：`frontend/public/samples/week4-inventory-flow-history-sample.csv`，21 行成功、0 行失败。
- 分析接口验证：`readinessCode=READY`，可输出缺货、呆滞、质量/报废风险统计。
- 未配置 `WMS_AI_API_KEY` 时，报告接口返回 `status=NOT_CONFIGURED`，规则型预警不受影响。

全量后端测试现状：

- `cd backend && mvn test` 当前仍有既有入库标签、锁货和 FIFO 相关断言失败。
- AI 导入与本轮新增分析接口编译验证通过；上述失败不属于本轮 AI 预警返工范围。

## 范围

### 本轮完成

| 活动 | 内容 | 输出 | 验收标准 |
| --- | --- | --- | --- |
| ACT-01 分析接口 | 基于当前库存余额与导入流水生成风险分析 | `GET /api/ai-warning/analysis/inventory-risks` | 接口返回数据准备状态、风险摘要和行级解释 |
| ACT-02 缺货风险 | 使用可用库存、近 7/30 天日均出库、库存覆盖天数和低储阈值判断 | `shortageRisk` | 能区分 `NONE/WATCH/HIGH/CRITICAL/DATA_UNPREPARED` |
| ACT-03 呆滞风险 | 使用最近入库、最近出库、30 天出库量和默认呆滞阈值判断 | `stagnationRisk` | 能解释长期未出库或高账龄低周转原因 |
| ACT-04 质量/报废风险 | 使用导入流水的 `quality_status` 和 `SCRAP` 记录做规则型提示 | `qualityRisk` | 能识别 `HOLD/NEAR_EXPIRY/EXPIRED/SCRAP` 等样例风险 |
| ACT-05 前端展示 | 在 AI 数据导入页展示后端分析摘要和风险表格 | Web 页面更新 | 导入后可刷新并查看分析结果 |
| ACT-06 AI 建议报告 | 调用 OpenAI-compatible API 生成仓储建议报告 | `GET /api/ai-warning/analysis/inventory-risk-report` | 配置 Key 后可生成报告，未配置时返回明确状态 |
| ACT-07 文档同步 | 把 Week 5 实现落点写入独立落地规格与执行计划 | 本文件和 `2026-06-29-ai-warning-scope.md` | 后续读者能区分 Week 4 规格冻结与 Week 5 落地实现 |

### 本轮不做

- 不新增真实模型训练、特征工程平台或在线推理服务。
- 不新增外部销售、新闻、天气、交通、供应中断等外部数据源。
- 不做自动补货、自动调仓、自动任务下发。
- 不让大模型决定风险等级；风险等级仍由本地规则计算。
- 不把导入样例中的 `board_code` 立刻迁移为 `inventory_tag_code`；该兼容口径已在规格中记录。
- 不在本轮校准真实业务阈值；当前阈值仍是可解释演示默认值。

## 实现落点

### 后端

- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryRiskAnalysisController.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryRiskAnalysisService.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryAdviceReportService.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryAdviceReportResponse.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryRiskAnalysisResponse.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryRiskSummary.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryRiskRow.java`
- `backend/src/main/java/com/scut/wms/aiwarning/AiInventoryRiskLevel.java`

分析输入：

- 当前库存余额：复用 `InventoryTransactionMapper.selectInventoryBalances(...)`，保持与库存查询页数量口径一致。
- 导入流水：读取 `ai_inventory_flow_history`，不写入核心库存事务表。
- 物料阈值：读取 `material.low_stock_qty` 作为低储兜底阈值。

### 前端

- `frontend/src/api/aiWarningImport.js`
- `frontend/src/views/inventory/InventoryAiImportView.vue`

展示内容：

- 数据准备状态：`READY/PARTIAL/NOT_READY`。
- 风险摘要：分析对象数、缺货高风险、呆滞高风险、质量风险。
- 行级指标：可用量、近 7 天日均出库、库存覆盖天数。
- 三类风险：缺货、呆滞、质量/报废风险。
- 触发原因：库存覆盖、低储阈值、最近出库、质量状态等可解释文本。
- AI 建议报告：调用大模型 API 生成 Markdown 报告，未配置 API Key 时展示提示。

配置项：

- `WMS_AI_API_KEY`
- `WMS_AI_BASE_URL`，默认 `https://api.openai.com/v1`
- `WMS_AI_MODEL`，默认 `gpt-4o-mini`
- `WMS_AI_API_FORMAT`，默认 `auto`；DeepSeek base url 默认走 `/chat/completions`，其他默认走 `/responses`

## 规则口径

### 缺货风险

默认阈值：

- `lead_time_days = 7`
- `safety_stock_days = 3`
- `replenishment_window = 10`

规则：

1. `available_qty <= 0` -> `CRITICAL`
2. 有出库历史且 `days_of_cover <= replenishment_window` -> `HIGH`
3. `available_qty <= material.low_stock_qty` -> `WATCH`
4. 历史数据缺失 -> `DATA_UNPREPARED`
5. 未命中以上条件 -> `NONE`

### 呆滞风险

默认阈值：

- `stagnant_days_threshold = 45`

规则：

1. `on_hand_qty <= 0` -> `NONE`
2. 历史数据缺失 -> `DATA_UNPREPARED`
3. 最近出库间隔达到 45 天且近 30 天出库量为 0 -> `HIGH`
4. 库存账龄大于等于 30 天且近 30 天出库量低于账面库存的 30% -> `WATCH`
5. 未命中以上条件 -> `NONE`

### 质量/报废风险

规则：

1. 有 `SCRAP` 流水或最新 `quality_status=EXPIRED` -> `EXPIRED`
2. 最新 `quality_status=HOLD/NEAR_EXPIRY` -> `HIGH`
3. 历史数据缺失 -> `DATA_UNPREPARED`
4. 未命中以上条件 -> `NONE`

## 相关文档检查

已同步：

- `docs/specs/2026-06-23-ai-warning-scope.md`：保留 Week 4 方向冻结与字段规则基线，不承载本轮落地实现细节。
- `docs/specs/2026-06-29-ai-warning-scope.md`：新增 Iteration 5 FR-09 AI 预警功能落地规格，记录接口、页面、数据输入、规则口径和验收标准。

暂不需要新增：

- 不新增新的 `docs/iterations/` 文件：Iteration 5 总体规格已由 `docs/specs/2026-06-27-iter5-batch-inbound-outbound-requirements.md` 承接；该文件明确 Iteration 5 主任务不包含 AI 预警，本轮作为 FR-09 返工补齐记录在本执行计划中即可。
- 不新增验收测试文档：当前已有接口导入和分析验证记录；若后续把 FR-09 纳入正式验收，应再补 `docs/tests/acceptence-tests/` 下对应步骤。

建议后续同步：

- 若 FR-09 被纳入课程最终验收，应补一份 AI 预警验收步骤，至少覆盖样例导入、分析状态变更、缺货风险、呆滞风险和质量/报废风险展示。
- 若确认 FR-08/FR-09 已完全从返工状态关闭，可清理 `docs/exec-plans/product-debt-tracker.md` 中的 `PD-017`。

## 风险与后续

- 当前仍是规则型预警，不是已训练 AI 模型。
- 缺货与呆滞阈值仍是演示默认值，未经过真实业务回测。
- 质量/报废风险当前主要依赖导入样例的 `quality_status` 和 `SCRAP`，尚未建立生产日期、有效期、保质期和批次级质量追溯。
- 若后续要进入真实 AI，需要先补标签来源、样本规模、评价指标和人工确认边界。
