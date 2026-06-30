# WMS 分享 ASCII 图稿

Date: 2026-07-01

## 使用约束

- 本文档用于放在分享无序列表项下面，不作为 PPT 内嵌图。
- 每个分享点对应一张局部 ASCII 图。
- 图只表达当前实现事实，不写口播提示。
- 事实优先级：当前代码 > `schema.sql` / Mapper XML > 文档业务叫法。
- 每行控制在 90 个字符以内。

## 第 1 部分：蒋显铭

- Spring Boot 3 架构分层（Controller -> Service -> Mapper -> DB）

```text
HTTP /api/...
    |
    v
+------------------+     +------------------+     +------------------+
| Controller       | --> | Service          | --> | Mapper           |
| 接收请求/参数校验 |     | 业务规则/事务     |     | MyBatis-Plus/XML |
+------------------+     +------------------+     +------------------+
                                                               |
                                                               v
                                                     +------------------+
                                                     | MySQL tables     |
                                                     | schema.sql       |
                                                     +------------------+
```

- 16 张表的设计逻辑和关联关系

```text
基础资料
+----------+   +----------+   +-------------------+   +-----------+
| supplier |   | material |   | material_container|   | container |
+----------+   +----------+   +-------------------+   +-----------+
      \             |                    |
       \            v                    v
        \      +-----------+       +-------------+
         +---> | warehouse | ----> | location    |
               +-----------+       +-------------+

入库主线                      库存核心
+---------------+ 1..n +----------------+ 1..n +---------------+
| inbound_order | ---> | inbound_line   | ---> | inventory_tag |
+---------------+      +----------------+      +---------------+
                                                   |       |
                                                   v       v
                                           +-------------+ +--------------+
                                           | movement    | | balance      |
                                           +-------------+ +--------------+

出库/占用
+----------------+ 1..n +---------------+     +---------------+
| outbound_order | ---> | outbound_line | --> | inventory_lock|
+----------------+      +---------------+     +---------------+
                                                   |
+---------------+                                  v
| inventory_hold| --------------------------> inventory_tag
+---------------+

AI 导入
+-----------------+ 1..n +---------------------------+
| ai_import_batch | ---> | ai_inventory_flow_history |
+-----------------+      +---------------------------+
```

- Token 登录前后端打通

```text
LoginView
   |
   | POST /api/auth/login  username=admin password=123456
   v
+----------------+      +----------------+
| AuthController | ---> | AuthService    |
+----------------+      | 固定校验账号   |
                        | 返回 demo-token|
                        +----------------+
                                  |
                                  v
                         localStorage / Pinia
                                  |
                                  | Authorization: Bearer demo-token-admin
                                  v
                         GET /api/auth/me
                                  |
                                  v
                         AuthService 校验固定 token
```

- 入库单 CRUD 后端接口 -> 这就是“货从哪里来”的入口

```text
/api/inbound-orders
   |
   +-- GET    list(status/inboundNo/supplier)
   +-- POST   create()
   +-- PUT    update(id)
   +-- POST   cancel(id)
   +-- POST   release(id)
   |
   v
+---------------------+
| InboundOrderService |
+---------------------+
   |
   +-- inbound_order       入库单头
   +-- inbound_order_line  物料/供应商/库位/容器/计划数量
   |
   | release()
   v
+---------------------+
| inventory_tag       |
| IT:v1:入库单:行:序号 |
| status = PRINTED    |
+---------------------+
   |
   v
后续扫码入库时变成 RECEIVED，并进入库存流水/余额
```

## 第 3 部分：梁喆栋

- 出库单创建 -> FIFO 推荐算法（按入库时间优先分配，保证先进先出）

```text
POST /api/outbound-orders
        |
        v
+----------------------+       +----------------------+
| outbound_order       | 1..n  | outbound_order_line  |
| status = DRAFT       | --->  | material/planned_qty |
+----------------------+       +----------------------+

GET /api/outbound-orders/{id}/recommendations
        |
        v
+-------------------------------+
| OutboundRecommendationService |
+-------------------------------+
        |
        v
selectFifoRecommendations(material, warehouseIds)
        |
        v
+---------------------------------------------------+
| inventory_tag                                     |
| status=RECEIVED, remain>0, no active hold         |
| ORDER BY received_at ASC, id ASC                  |
+---------------------------------------------------+
        |
        v
推荐列表：先入库的库存标签排在前面
```

- 锁货 -> 带单出库 -> 散件出库后零头处理

```text
POST /api/outbound-orders/{id}/release-and-lock
        |
        v
+-------------+   FIFO candidates   +----------------+
| LockService | ------------------> | inventory_tag  |
+-------------+                     | RECEIVED only  |
        |                           +----------------+
        | update tag.status=LOCKED
        | insert inventory_lock
        v
+----------------+        +----------------+
| inventory_lock | -----> | outbound_order |
| status=LOCKED  |        | status=LOCKED  |
+----------------+        +----------------+

POST /api/outbound/pick-with-order
        |
        v
+------------------------+
| OutboundPickingService |
+------------------------+
        |
        +-- 校验：物料匹配 / 推荐方案 / FIFO / 封存手锁
        |
        +-- insert inventory_movement(type=OUTBOUND_PICK)
        +-- inventory_balance.on_hand_qty -= pickQty
        +-- inventory_tag.picked_qty += pickQty
        |
        v
若 picked_qty < board_qty  -> 库存标签保留零头
若 picked_qty >= board_qty -> inventory_tag.status = SHIPPED
```

- 库存追溯 SQL：N 表联查还原一件货从入库到出库的完整轨迹

```text
inventory_tag_code
        |
        v
+---------------+
| inventory_tag |
+---------------+
   | inbound_order_id        | inbound_order_line_id
   v                         v
+---------------+       +------------------+
| inbound_order |       | inbound_order_line|
+---------------+       +------------------+
                                  |
                                  v
                         +----------------+
                         | material       |
                         +----------------+
                                  |
inventory_tag.location_id         |
   |                              |
   v                              v
+------------------+       +--------------------+
| storage_location |       | inventory_movement |
+------------------+       | INBOUND_RECEIVE    |
        |                  | OUTBOUND_PICK      |
        v                  +--------------------+
+-----------+                       |
| warehouse |                       | outbound ids when shipped
+-----------+                       v
                            +----------------+   +---------------+
                            | outbound_order |   | outbound_line |
                            +----------------+   +---------------+
```

实现口径：

- 当前 `GET /api/inventory-tags/{code}/trace` 更偏库存标签当前追溯视图。
- 该接口联入入库单、入库明细、物料、库位和最近一条库存流水。
- 完整入库到出库轨迹需要结合 `inventory_movement` 多条流水查看。
