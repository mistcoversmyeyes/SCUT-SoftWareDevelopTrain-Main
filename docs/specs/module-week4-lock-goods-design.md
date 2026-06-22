# Week 4 设计规格：锁货功能（Lock Goods）

## 背景输入

本设计服务于出库管理增强。当前出库模块在释放出库单后直接进入拣货，没有库存预分配机制。本功能在释放出库单时按 FIFO 规则提前锁定看板库存，防止多张出库单同时争抢同一批货，并支持带单/不带单出库、强制出库、锁货管理。

产品背景第一事实源为 `docs/references/Course PPT/WMS仓储管理系统--产品介绍资料.pdf`。一汽大众专题课程资料当前未在本地 references 中归档；需要引用时先补充资料并更新 `docs/references/index.md`。

## 范围

### 新增

- 出库单释放并加锁：释放时按多选仓库范围 FIFO 锁定看板
- 看板新状态 LOCKED：锁定后仅关联出库单可拣货
- 带单出库：扫出库单二维码 → 查看锁定清单 → 跳扫码页出库
- 不带单出库：直接扫看板出库（强制抢锁+审计）
- 带单强制出库：扫看板抢锁 → 算本单 → 重新 FIFO
- 通用扫码页面：支持 normal / force / no-order 三种模式
- 锁货管理页面：锁记录查看 / 解锁 / 重新分配 / 强制审计日志
- 挂起拣货：暂停后出库单保持 PICKING，可从详情页继续

### 废弃

- 原 release 接口 → release-and-lock 替代
- 原 start-picking / suspend → 不再需要
- 原 outbound/scan 接口 → 拆分为 pick-with-order / pick-no-order
- 原 outbound/recommend 接口 → 锁货后无需推荐
- 原拣货推荐视图 → 锁货清单替代

## 数据模型

### 新表 inventory_lock

```sql
CREATE TABLE inventory_lock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  outbound_order_id BIGINT NOT NULL,
  outbound_order_line_id BIGINT NOT NULL,
  kanban_board_id BIGINT NOT NULL,
  material_id BIGINT NOT NULL,
  lock_qty DECIMAL(18, 3) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'LOCKED',
  stolen_by_order_id BIGINT DEFAULT NULL,
  stolen_at DATETIME DEFAULT NULL,
  unlocked_at DATETIME DEFAULT NULL,
  unlocked_by VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_lock_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_order(id),
  CONSTRAINT fk_lock_line FOREIGN KEY (outbound_order_line_id) REFERENCES outbound_order_line(id),
  CONSTRAINT fk_lock_kanban FOREIGN KEY (kanban_board_id) REFERENCES kanban_board(id),
  INDEX idx_lock_order (outbound_order_id),
  INDEX idx_lock_kanban (kanban_board_id),
  INDEX idx_lock_status (status)
);
```

### kanban_board 新增字段

- `locked_by_order_id BIGINT`
- `locked_by_order_line_id BIGINT`

### inventory_movement 新增字段

- `force_outbound TINYINT(1) DEFAULT 0`
- `force_remark VARCHAR(255)`

### outbound_order 新增字段

- `qrcode VARCHAR(255)`

### 看板状态枚举

`PRINTED → RECEIVED → LOCKED → SHIPPED`

### 出库单状态

`DRAFT → LOCKED → PICKING → COMPLETED`

## 后端设计

### 新包 com.scut.wms.lock

| 接口 | 路径 | 用途 |
|------|------|------|
| POST | /api/outbound-orders/{id}/release-and-lock | FIFO 锁定看板 |
| GET | /api/locks | 锁记录列表 |
| POST | /api/locks/{id}/unlock | 解锁 |
| POST | /api/outbound-orders/{id}/reassign | 重新 FIFO 分配 |
| GET | /api/locks/force-logs | 强制出库审计日志 |
| GET | /api/outbound-orders/{id}/qr-info | 出库单锁定物料清单 |
| POST | /api/outbound/pick-with-order | 带单出库(正常) |
| POST | /api/outbound/pick-with-order/force | 带单出库(强制) |
| POST | /api/outbound/pick-no-order | 不带单出库 |

## 前端设计

### 菜单

```
📤 出库管理
   ├ 出库单              /outbound/orders
   ├ 带单出库            /outbound/pick-with-order
   ├ 不带单出库          /outbound/pick-no-order
   ├ 锁货管理            /outbound/locks
   └ 出库历史            /outbound/history
```

### 页面

| 页面 | 说明 |
|------|------|
| OutboundOrderListView | 操作列：编辑/释放并加锁/查看打印/取消 |
| OutboundPickWithOrderView | 扫出库单码→锁定清单→跳扫码页→暂停 |
| OutboundPickNoOrderView | 跳扫码页(不带单模式) |
| OutboundScanPage | 通用扫码页，支持 normal/force/no-order 三种模式 |
| OutboundLockView | 出库单总览+锁明细+解锁/重分配+审计标签页 |

## 验证

- 后端：mvn test 覆盖 lock/unlock/reassign、releaseAndLock FIFO、pick 三种模式
- 前端：npm test + npm run build
- 联调：完整出库流程 + 强制出库 + 锁管理操作
