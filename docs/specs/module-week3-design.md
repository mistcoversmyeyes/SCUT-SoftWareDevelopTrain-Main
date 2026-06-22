# Week 3 设计规格：出库管理 + 基础数据完善 + 入库增强

## 背景输入

本设计服务于 Week 3 周迭代。当前代码基线为 Week 2 产出：入库单 CRUD、看板生成/打印、扫码入库、库存余额/追溯、看板追溯。本周在此基础上完成出库闭环、基础数据 CRUD、入库模块增强和看板生命周期完善。

产品背景第一事实源为 `docs/references/Course PPT/WMS仓储管理系统--产品介绍资料.pdf`。课程要求来自 `docs/references/Course PPT/0.华南理工大学授课(2).pdf`。

## 范围

### 本周新增

**出库管理：**
- 出库单：列表、创建、编辑、释放、取消、查看/打印
- 出库扫码：看板码出库，FIFO 先进先出，扣减库存，更新看板状态
- 出库历史：按时间范围查询已完成/已取消的出库单

**基础数据 CRUD：**
- 供应商管理：列表、创建、编辑、启停用
- 物料信息管理：列表、创建、编辑、关联供应商和包装类型
- 器具管理：列表、创建、编辑、启停用
- 仓库库位管理：列表、创建、编辑

**入库增强：**
- 查看/打印入库单（替代原来的"打印入库单"按钮）：标签页详情 + 可复制单号 + 打印
- 查看/打印看板（替代原来的"打印看板"按钮）：标签页看板列表 + 二维码 + 打印
- 入库历史：按时间范围查询已完成/已取消的入库单
- 看板列表：多维度筛选，查看看板生命周期状态

**库存增强：**
- Dashboard 首页：统计卡片 + 高低储预警列表
- 库存高低储预警：物料级别 low_stock_qty / high_stock_qty

### 明确不做

- 不做 Android/PDA 原生工程
- 不做真实打印机/标签机协议
- 不做 PDF 导出
- 不做失败扫码日志持久化
- 不做复杂权限角色体系
- 不做转包、封存、解封、退库
- 不做 AI 融合功能

## 数据模型变更

### 新增表

```sql
CREATE TABLE container_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  container_code VARCHAR(64) NOT NULL UNIQUE,
  container_name VARCHAR(128) NOT NULL,
  capacity_qty DECIMAL(18, 3),
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE outbound_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  outbound_no VARCHAR(64) NOT NULL UNIQUE,
  supplier_id BIGINT NOT NULL,
  purpose VARCHAR(64),
  source_doc_no VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  remark VARCHAR(255),
  released_at DATETIME,
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_outbound_order_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  INDEX idx_outbound_order_status (status)
);

CREATE TABLE outbound_order_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  outbound_order_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  material_id BIGINT NOT NULL,
  planned_qty DECIMAL(18, 3) NOT NULL,
  picked_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
  source_warehouse_id BIGINT NOT NULL,
  source_location_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_outbound_order_line UNIQUE (outbound_order_id, line_no),
  CONSTRAINT fk_outbound_line_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_order(id),
  CONSTRAINT fk_outbound_line_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_outbound_line_warehouse FOREIGN KEY (source_warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_outbound_line_location FOREIGN KEY (source_location_id) REFERENCES storage_location(id)
);
```

### 修改现有表

```sql
-- material: 新增包装容量和高低储字段
ALTER TABLE material
  ADD COLUMN container_type_id BIGINT,
  ADD COLUMN low_stock_qty DECIMAL(18, 3),
  ADD COLUMN high_stock_qty DECIMAL(18, 3),
  ADD CONSTRAINT fk_material_container FOREIGN KEY (container_type_id) REFERENCES container_type(id);

-- kanban_board: 新增已出库数量
ALTER TABLE kanban_board
  ADD COLUMN picked_qty DECIMAL(18, 3) NOT NULL DEFAULT 0 AFTER board_qty;
```

### 枚举扩展

- `kanban_board.status`：增加 `SHIPPED`（已出库）
- `inventory_movement.movement_type`：增加 `OUTBOUND_PICK`（出库拣货）
- `outbound_order.status`：`DRAFT / RELEASED / PARTIAL_SHIPPED / COMPLETED / CANCELLED`
- `outbound_order.purpose`：`PRODUCTION_PICK / RETURN_TO_SUPPLIER / OTHER`

## 后端设计

### 新领域包

| 包 | 职责 |
| --- | --- |
| `com.scut.wms.outbound` | 出库单 CRUD、释放、取消、打印 |
| `com.scut.wms.outbound.picking` | 出库扫码拣货（FIFO） |
| `com.scut.wms.masterdata` (扩展) | 基础数据完整 CRUD |
| `com.scut.wms.container` | 器具类型管理 |

### 后端新增接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/inbound-orders/{id}` | 获取单个入库单详情 |
| `GET` | `/api/kanbans?status=&inboundNo=&materialCode=` | 看板列表多维度查询 |
| `GET` | `/api/inbound-orders/{id}/kanbans` | 获取入库单对应的看板列表 |
| `POST` | `/api/outbound-orders` | 创建出库单 |
| `GET` | `/api/outbound-orders` | 出库单列表 |
| `GET` | `/api/outbound-orders/{id}` | 获取单个出库单 |
| `PUT` | `/api/outbound-orders/{id}` | 修改出库单 |
| `POST` | `/api/outbound-orders/{id}/release` | 释放出库单 |
| `POST` | `/api/outbound-orders/{id}/cancel` | 取消出库单 |
| `GET` | `/api/outbound-orders/{id}/print` | 获取出库单打印数据 |
| `POST` | `/api/outbound/scan` | 扫描看板码执行 FIFO 出库 |
| `POST` | `/api/container-types` | 创建器具类型 |
| `GET` | `/api/container-types` | 器具类型列表 |
| `PUT` | `/api/container-types/{id}` | 修改器具类型 |
| `PUT` | `/api/container-types/{id}/status` | 启停用器具类型 |
| `POST` | `/api/suppliers` | 创建供应商 |
| `PUT` | `/api/suppliers/{id}` | 修改供应商 |
| `PUT` | `/api/suppliers/{id}/status` | 启停用供应商 |
| `POST` | `/api/materials` | 创建物料 |
| `PUT` | `/api/materials/{id}` | 修改物料 |
| `PUT` | `/api/materials/{id}/status` | 启停用物料 |
| `POST` | `/api/warehouses` | 创建仓库 |
| `POST` | `/api/storage-locations` | 创建库位 |
| `GET` | `/api/dashboard/stats` | 首页统计数据 |

### 扫码出库事务（核心）

`POST /api/outbound/scan` 事务边界：

1. 按 `kanbanCode` 查询看板并 `FOR UPDATE` 加锁
2. 看板不存在 → 返回错误
3. 看板状态不是 `RECEIVED` → 返回"看板不可出库"
4. FIFO 校验：查询该物料在目标库位是否有更早入库的看板未出库，如有则提示"请优先出库更早批次"
5. 写入 `inventory_movement`：`movement_type = OUTBOUND_PICK`
6. 扣减 `inventory_balance.on_hand_qty`
7. 累加 `kanban_board.picked_qty`，若 `picked_qty >= board_qty` 则状态改为 `SHIPPED`
8. 提交事务

## 前端设计

### 菜单重组

```
🏠 首页
📋 基础数据
   ├ 供应商管理    /master-data/suppliers
   ├ 物料信息      /master-data/materials
   ├ 器具管理      /master-data/containers
   └ 仓库库位      /master-data/warehouses
📥 入库管理
   ├ 入库单        /inbound/orders
   ├ 入库扫码      /inbound/scan
   └ 入库历史      /inbound/history
📤 出库管理
   ├ 出库单        /outbound/orders
   ├ 出库扫码      /outbound/scan
   └ 出库历史      /outbound/history
📦 库存监控
   ├ 当前库存      /inventory/balances
   └ 库存追溯      /inventory/trace
🏷️ 看板信息
   ├ 看板列表      /kanbans/list
   └ 看板追溯      /kanbans/trace
```

### 前端改造/新增页面清单

| # | 页面组件 | 路由 | 操作 |
|------|---------|------|------|
| 1 | `InboundDetailView` | `/inbound/:id` | **改造**：替代打印弹窗，标签页详情+复制+打印 |
| 2 | `KanbanDetailView` | `/inbound/:id/kanbans` | **改造**：替代打印弹窗，看板列表+二维码+打印 |
| 3 | `InboundOrderListView` | `/inbound/orders` | **小改**：按钮文字"打印入库单"→"查看/打印入库单"，"打印看板"→"查看/打印看板" |
| 4 | `InboundHistoryView` | `/inbound/history` | **新增**：入库历史 |
| 5 | `KanbanListView` | `/kanbans/list` | **新增**：看板列表多维度筛选 |
| 6 | `OutboundOrderListView` | `/outbound/orders` | **新增**：出库单管理 |
| 7 | `OutboundScanView` | `/outbound/scan` | **新增**：出库扫码 |
| 8 | `OutboundHistoryView` | `/outbound/history` | **新增**：出库历史 |
| 9 | `DashboardView` | `/dashboard` | **改造**：统计卡片+预警列表 |
| 10 | `SupplierListView` | `/master-data/suppliers` | **新增**：供应商管理 |
| 11 | `MaterialListView` | `/master-data/materials` | **新增**：物料管理 |
| 12 | `ContainerListView` | `/master-data/containers` | **新增**：器具管理 |
| 13 | `WarehouseLocationView` | `/master-data/warehouses` | **新增**：仓库库位管理 |

### 关键 UI 改造细节

**入库单详情页 (`InboundDetailView`)：**
- 标签页模式，路由 `/inbound/:id`
- 上方：入库单基本信息（el-descriptions），单号后带复制按钮
- 中间：明细表格
- 底部：打印按钮（触发 window.print()）
- 打印按钮替换为"查看/打印入库单"

**看板详情/打印页 (`KanbanDetailView`)：**
- 标签页模式，路由 `/inbound/:id/kanbans`
- 横条物料标签风格：QR 码在左、信息在右
- 每张标签约 360px×100px，A4 纸纵向排列多张
- QR 码使用 `qrcode` 库生成（`npm install qrcode`）
- 每个看板码旁有复制按钮
- 打印按钮触发 window.print()，@media print 隐藏非打印元素

### 前端依赖新增

```bash
npm install qrcode
```

## 看板生命周期（完整）

```
释放入库单 → [PRINTED 已打印]
                 ↓ 扫码入库
            [RECEIVED 已入库]
                 ↓ 扫码出库 (FIFO)
            [SHIPPED 已出库]

任意环节 → [CANCELLED 已取消]（入库单/出库单取消时）
```

## 验证计划

后端：
- 运行 `mvn test`
- 覆盖：出库单 CRUD、释放/取消、FIFO 出库拣货、库存扣减、看板状态更新、基础数据 CRUD

前端：
- 运行 `npm test` + `npm run build`
- 覆盖：路由注册、菜单嵌套、关键组件渲染

手动联调：
1. 登录系统，验证新菜单结构
2. 基础数据：创建供应商、物料（关联包装）、器具、库位
3. 入库单：创建→释放→查看/打印入库单→查看/打印看板（验证二维码）
4. 入库扫码→查看库存→看板列表验证生命周期
5. 出库单：创建→释放
6. 出库扫码→FIFO 验证→库存扣减→看板状态变为 SHIPPED
7. 入库历史/出库历史查看
8. Dashboard 首页统计+预警
