# Week 2 采购入库核心功能设计

## 背景输入

本设计服务于 Week 2 周迭代：完成 WMS 采购入库相关功能开发。产品背景第一事实源为 `res/WMS仓储管理系统--产品介绍资料.pdf`。PDF 图片给出的可见参考包括入库单列表、编辑入库单、打印入库单、打印看板、PDA 入库扫码结果、库存看板和看板信息页面。

当前代码现状：

- 前端已有 Vue 3 + Vite + Element Plus 后台骨架，包含登录、左侧菜单、顶部标签页和占位页。
- 后端已有 Spring Boot 登录演示接口，尚无业务持久化层。
- 本周必须接入 MySQL 8，并使用 MyBatis-Plus + MyBatis 手写 SQL 完成持久化与复杂查询。

本设计先保证采购入库闭环可演示、可持久化、可追溯，不把本周范围扩展成完整 WMS 平台。

## 范围

本周实现以下功能：

- 基础信息：供应商、物料、仓库、库位作为内置主数据，支持入库流程下拉选择与展示。
- 入库单：列表、筛选、创建、修改、释放、取消、打印。
- 看板：释放入库单后生成唯一看板，支持浏览器打印看板。
- 扫码入库：Web 管理端输入看板码，支持手输和扫码枪回车。
- 库存：扫码成功后写库存流水、更新当前库存余额。
- 追溯：库存追溯和看板追溯。
- 数据持久化：MySQL 8，后端启动时自动执行建表和演示数据初始化 SQL。

明确不做：

- 不做 Android/PDA 原生工程。
- 不做移动端 Web 摄像头扫码。
- 不做供应商、物料、仓库、库位的完整维护页面。
- 不接真实打印机、标签机或打印协议。
- 不做 PDF 导出。
- 不做失败扫码日志持久化。
- 不做库存冲销、反入库、出库、调拨、盘点。
- 不做复杂权限角色体系。

## 数据模型

最终持久化模型采用 9 张表：

```text
supplier
material
warehouse
storage_location
inbound_order
inbound_order_line
kanban_board
inventory_movement
inventory_balance
```

详细 ER 图、原始 11 表 schema、范式审查和化简过程见 `docs/specs/module-inbound-data-model-review.md`。

关键裁决：

- `container_type` 延后，不在本周引入器具容量、装箱规则、拆板或合板语义。
- `scan_record` 不建。成功扫码直接形成 `inventory_movement`；失败扫码只返回错误。
- 入库单状态使用 `DRAFT / RELEASED / PARTIAL_RECEIVED / COMPLETED / CANCELLED`。
- 看板状态使用 `PRINTED / RECEIVED / CANCELLED`。
- `inventory_movement` 是可扩展库存流水，不写死为只支持入库；规格上保留 `movement_type`、`source_type/source_id` 语义。
- `inventory_balance` 只保存当前库存余额，唯一粒度为 `material_id + warehouse_id + storage_location_id`。
- 入库单号、看板码、库存流水号全局唯一且不可复用。
- 看板码建议带类型与版本前缀，例如 `KB:v1:<业务码>`，为后续 PDA 或不同码制预留空间。

## 后端设计

后端按领域分包：

- `masterdata`：供应商、物料、仓库、库位只读能力。
- `inbound`：入库单、入库明细、看板生成、打印数据。
- `inventory`：扫码入库、库存余额、库存流水、看板追溯。

主要接口：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/master-data/options` | 返回供应商、物料、仓库、库位下拉数据 |
| `GET` | `/api/inbound-orders` | 入库单列表，支持状态、单号、供应商筛选 |
| `POST` | `/api/inbound-orders` | 创建草稿入库单和明细 |
| `PUT` | `/api/inbound-orders/{id}` | 修改入库单和明细 |
| `POST` | `/api/inbound-orders/{id}/release` | 释放入库单并生成看板 |
| `POST` | `/api/inbound-orders/{id}/cancel` | 取消未收货入库单 |
| `GET` | `/api/inbound-orders/{id}/print` | 返回入库单打印数据 |
| `GET` | `/api/inbound-orders/{id}/kanbans/print` | 返回看板打印数据 |
| `POST` | `/api/inventory/scan-inbound` | 输入看板码并执行扫码入库 |
| `GET` | `/api/inventory/balances` | 当前库存查询 |
| `GET` | `/api/inventory/movements` | 库存流水追溯 |
| `GET` | `/api/kanbans/{kanbanCode}/trace` | 看板追溯 |

### 扫码入库事务

`POST /api/inventory/scan-inbound` 是本周最关键事务边界：

1. 按 `kanbanCode` 查询看板并加锁。
2. 看板不存在：返回错误，不落库。
3. 看板状态不是 `PRINTED`：返回重复扫码或不可入库错误，不新增流水。
4. 校验入库单状态为 `RELEASED` 或 `PARTIAL_RECEIVED`。
5. 写入 `inventory_movement`，`movement_type = INBOUND_RECEIVE`。
6. 按物料、仓库、库位累加 `inventory_balance`。
7. 更新看板为 `RECEIVED`。
8. 累加入库明细 `received_qty`。
9. 重新计算入库单状态为 `PARTIAL_RECEIVED` 或 `COMPLETED`。
10. 提交事务并返回扫码结果。

重复扫码不得重复增加库存。

## 前端设计

前端沿用当前仓库已有后台风格，不引入新视觉体系：

- 复用 `MainLayout`：顶部深色栏、左侧菜单、顶部标签页、主内容区。
- 复用 Element Plus 表格、表单、弹窗、按钮、标签、描述列表。
- 入库模块只增强主内容区业务页面。
- 打印页面可以使用独立黑白表格和标签样式，贴近 PDF 参考。

建议菜单：

```text
入库管理
  入库单
  扫码入库
库存监控
  当前库存
  库存追溯
看板信息
  看板追溯
```

页面：

- `InboundOrderListView`：状态筛选、单号筛选、供应商筛选、入库单表格。操作包括创建、修改、释放/生成看板、打印入库单、打印看板、取消。
- `InboundOrderFormView`：创建/修改入库单。头部字段包括供应商、来源单号、备注；明细行包括物料、计划数量、目标仓库、目标库位。
- `InboundPrintView`：入库单浏览器打印页，黑白表格。
- `KanbanPrintView`：看板标签浏览器打印页，一页多张标签，显示看板码、物料、供应商、库位、日期、数量和二维码视觉区域。
- `InboundScanView`：Web 扫码入库页，输入框自动聚焦，支持手输和扫码枪回车，展示成功或失败结果。
- `InventoryBalanceView`：当前库存查询。
- `InventoryTraceView`：库存流水追溯。
- `KanbanTraceView`：按看板码展示看板状态、入库单、物料、扫码时间和库存流水。

## 打印设计

入库单打印：

- 使用浏览器打印，不导出 PDF。
- 样式为黑白表格，参考 PDF 中入库单样式。
- 内容包括供应商、入库类型、来源单号、日期、物料明细、数量、库位、合计、备注。

看板打印：

- 使用浏览器打印。
- 一页多张看板标签。
- 每张看板显示看板码、物料编码/名称、供应商、库位、日期、数量和状态。
- 本周可先展示二维码视觉块和可复制看板码；若依赖可控，再接前端二维码库。

## 追溯设计

库存追溯：

- 查询 `inventory_movement`。
- 支持按物料、仓库、库位、入库单号、看板码、时间筛选。
- 展示流水号、物料、库位、数量、来源看板、发生时间。

看板追溯：

- 输入看板码。
- 展示看板状态、所属入库单、供应商、物料、计划数量、是否已入库、扫码时间和库存流水。
- 未扫码看板展示 `PRINTED` 状态和待入库信息。

## 错误处理

- 入库单创建/修改时，供应商、物料、数量、库位必填；数量必须大于 0。
- 非 `DRAFT` 且已有收货的入库单不允许随意修改明细。
- 只有 `DRAFT` 入库单允许释放并生成看板。
- 释放接口必须幂等：已释放入库单重复释放不得重复生成看板。
- 看板码不存在时，扫码接口返回“未找到看板”。
- 看板已入库时，扫码接口返回“重复扫码”，不新增库存流水。
- 看板已取消时，扫码接口返回“看板不可入库”。
- 入库单未释放或已取消时，扫码接口返回“单据状态不允许入库”。
- MySQL 连接失败时后端启动失败，并在日志中暴露数据源配置问题。

## 验证计划

后端：

- 运行 `mvn test`。
- 覆盖入库单创建、修改、释放生成看板、扫码入库、重复扫码、库存余额更新、库存追溯、看板追溯。

前端：

- 运行 `npm test`。
- 运行 `npm run build`。
- 覆盖路由、菜单、关键 API mock 和页面基本渲染。

手动联调：

1. 登录系统。
2. 创建入库单。
3. 释放入库单并生成看板。
4. 打印入库单。
5. 打印看板。
6. 使用看板码扫码入库。
7. 查看当前库存。
8. 查看库存追溯。
9. 使用看板码查看看板追溯。

## 验收标准

- MySQL 持久化可用，刷新页面后数据仍存在。
- 后端启动后能自动建表并初始化演示数据。
- 入库单能创建、修改、释放、取消和打印。
- 看板能生成、打印、扫码入库。
- 重复扫码不会重复增加库存。
- 库存余额与库存流水一致。
- 看板追溯能从看板码追到入库单、物料和库存流水。
- UI 风格沿用当前后台，不引入新的视觉体系。

## 产品债

以下问题不阻塞本周实现，但后续进入产品债或后续规格：

- 器具、容器、包装容量和装箱规则。
- 失败扫码审计和设备日志。
- PDA/Android 或移动端 Web 扫码。
- 出库、调拨、盘点、退库、库存冲销。
- 真实打印机、标签机协议和 PDF 导出。
- 供应商、物料、仓库、库位的完整维护流程。
