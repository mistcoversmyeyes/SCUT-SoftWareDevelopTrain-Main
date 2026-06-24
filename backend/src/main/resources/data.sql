-- ==========================================================================
-- 一汽大众佛山工厂 WMS 初始化数据
-- 吉耀仓 & 富利仓 — 真实模拟场景
-- ==========================================================================

-- ============================
-- 容器类型（周转箱 / 托盘 / 铁笼）
-- ============================
INSERT INTO container_type (id, container_code, container_name, capacity_qty, status) VALUES
(1, 'KLT-4320', '群益蓝色周转箱 KLT-4320', 100, 'ENABLED'),
(2, 'KLT-6422', '凤阳灰色周转箱 KLT-6422', 50,  'ENABLED'),
(3, 'EP-1200',  '久达标准塑料托盘 EP-1200(1.2m×1.0m)', 500, 'ENABLED'),
(4, 'GLT-6010', '久达镀锌线棒笼车 GLT-6010', 200, 'ENABLED');

-- ============================
-- 供应商
-- ============================
INSERT INTO supplier (id, supplier_code, supplier_name, contact_name, contact_phone, status) VALUES
(1, '8KH', '佛山华翔金属件有限公司',        '张振华', '13929130547', 'ENABLED'),
(2, '4MU', '宁波劳伦斯汽车内饰件有限公司',  '李雪梅', '18584822168', 'ENABLED'),
(3, '7KL', '长春一汽富维零部件有限公司',    '王立军', '18643191023', 'ENABLED');

-- ============================
-- 物料（一汽大众原厂零件编码）
-- ============================
INSERT INTO material (id, material_code, material_name, specification, unit, supplier_id, low_stock_qty, high_stock_qty, status) VALUES
(1, '5HG.807.109.C', '前保险杠安装支架总成',        'PA66-GF30 注塑件 / 适配 MQB-A 平台',         '件', 1, 80,  600,  'ENABLED'),
(2, '5WD.723.913.C', '加速踏板模块总成',            '电子油门 / PWG 非接触式传感器',                '件', 1, 50,  400,  'ENABLED'),
(3, '5Q0.803.219.D', '车身前纵梁连接板',            'HC340/590DP 高强度双相钢 / 料厚 2.0mm',       '件', 3, 60,  500,  'ENABLED'),
(4, '3Q0.864.245.A', '后轮罩内板总成（左）',        'DC06 深冲钢 / 表面电泳黑漆',                   '件', 3, 40,  350,  'ENABLED'),
(5, '5WA.857.031.B', '组合仪表板加强横梁总成',      'AlMg3 铝合金挤压型材 / 表面阳极氧化',          '件', 2, 30,  300,  'ENABLED'),
(6, '8W0.807.541.C', '前纵梁下部加强件',            '22MnB5 热成型钢 / 激光拼焊 B 柱加强板配套件', '件', 3, 50,  450,  'ENABLED');

-- ============================
-- 物料-容器关联（1:N）
-- ============================
INSERT INTO material_container_type (material_id, container_type_id, is_default) VALUES
-- 前保险杠支架：常用周转箱，大批量走托盘
(1, 1, 1),
(1, 3, 0),
-- 加速踏板模块：只走周转箱
(2, 1, 1),
-- 车身前纵梁连接板：托盘为主，偶尔笼车
(3, 3, 1),
(3, 4, 0),
-- 后轮罩内板：只走托盘（体积大）
(4, 3, 1),
-- 仪表板横梁：小周转箱为主
(5, 2, 1),
(5, 1, 0),
-- 前纵梁加强件：笼车为主，大批量走托盘
(6, 4, 1),
(6, 3, 0);

-- ============================
-- 仓库
-- ============================
INSERT INTO warehouse (id, warehouse_code, warehouse_name, status) VALUES
(1, 'WH-JY', '吉耀仓（佛山三水基地）',  'ENABLED'),
(2, 'WH-FL', '富利仓（广州花都基地）',  'ENABLED');

-- ============================
-- 库位（箱容量）
-- ============================
INSERT INTO storage_location (id, warehouse_id, location_code, location_name, max_capacity, status) VALUES
-- 吉耀仓
(1,  1, 'A-01', '高位货架 A 区 01 号',   20,  'ENABLED'),
(2,  1, 'A-02', '高位货架 A 区 02 号',   20,  'ENABLED'),
(3,  1, 'A-03', '高位货架 A 区 03 号',   20,  'ENABLED'),
(4,  1, 'B-01', '地面托盘区 B 区 01 号', 15,  'ENABLED'),
(5,  1, 'B-02', '地面托盘区 B 区 02 号', 15,  'ENABLED'),
(6,  1, 'C-01', '小件流利料架 C 区 01',  30,  'ENABLED'),
(7,  1, 'C-02', '小件流利料架 C 区 02',  30,  'ENABLED'),
-- 富利仓
(8,  2, 'D-01', '地面堆存 D 区 01 号',   10,  'ENABLED'),
(9,  2, 'D-02', '地面堆存 D 区 02 号',   10,  'ENABLED'),
(10, 2, 'E-01', '窄巷道高位货架 E 区 01', 20,  'ENABLED');

-- ==========================================================================
-- 入库单 #1 — 5 月 20 日到货，部分收货
-- ==========================================================================
INSERT INTO inbound_order (id, inbound_no, supplier_id, source_doc_no, status, remark, released_at) VALUES
(1, 'IN-20260520-001', 1, 'PO-20260515-001', 'PARTIAL_RECEIVED', '5/15 采购单首车到货，余数 5/22 补发', '2026-05-20 08:30:00');

INSERT INTO inbound_order_line (id, inbound_order_id, line_no, material_id, supplier_id, planned_qty, received_qty, target_warehouse_id, target_location_id, container_type_id) VALUES
(1, 1, 1, 1, 1, 250, 200, 1, 1, 1),   -- 前保险杠支架 / KLT-4320(100件/箱) → 3箱(100+100+50)，已收2箱=200
(2, 1, 2, 2, 1, 180, 180, 1, 2, 1);   -- 加速踏板模块 / KLT-4320(100件/箱) → 2箱(100+80)，全部已收

-- 行 1 库存标签（3 箱）：前 2 箱已入库，第 3 箱待收
INSERT INTO inventory_tag (id, inventory_tag_code, inbound_order_id, inbound_order_line_id, location_id, container_type_id, board_qty, status, printed_at, received_at) VALUES
(1,  'IT:v1:IN-20260520-001:1:1', 1, 1, 1, 1, 100, 'RECEIVED', '2026-05-20 08:35:00', '2026-05-20 09:12:00'),
(2,  'IT:v1:IN-20260520-001:1:2', 1, 1, 1, 1, 100, 'RECEIVED', '2026-05-20 08:35:00', '2026-05-20 09:14:00'),
(3,  'IT:v1:IN-20260520-001:1:3', 1, 1, 1, 1, 50,  'PRINTED',  '2026-05-20 08:35:00', NULL);

-- 行 2 库存标签（2 箱）：全部已入库
INSERT INTO inventory_tag (id, inventory_tag_code, inbound_order_id, inbound_order_line_id, location_id, container_type_id, board_qty, status, printed_at, received_at) VALUES
(4,  'IT:v1:IN-20260520-001:2:1', 1, 2, 2, 1, 100, 'RECEIVED', '2026-05-20 08:35:00', '2026-05-20 09:30:00'),
(5,  'IT:v1:IN-20260520-001:2:2', 1, 2, 2, 1, 80,  'RECEIVED', '2026-05-20 08:35:00', '2026-05-20 09:32:00');

-- inventory_movement（行 1 已收 2 箱）
INSERT INTO inventory_movement (id, movement_no, movement_type, source_type, source_id, inventory_tag_id, material_id, warehouse_id, storage_location_id, planned_location_id, qty, occurred_at, operator_name) VALUES
(1, 'MV-20260520091200-A1B2C3D4', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 1, 1, 1, 1, 1, 1, 100, '2026-05-20 09:12:00', '张振华'),
(2, 'MV-20260520091400-E5F6G7H8', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 2, 2, 1, 1, 1, 1, 100, '2026-05-20 09:14:00', '张振华');

-- 行 2 已收 2 箱
INSERT INTO inventory_movement (id, movement_no, movement_type, source_type, source_id, inventory_tag_id, material_id, warehouse_id, storage_location_id, planned_location_id, qty, occurred_at, operator_name) VALUES
(3, 'MV-20260520093000-I9J0K1L2', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 4, 4, 2, 1, 2, 2, 100, '2026-05-20 09:30:00', '张振华'),
(4, 'MV-20260520093200-M3N4O5P6', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 5, 5, 2, 1, 2, 2, 80,  '2026-05-20 09:32:00', '张振华');

-- ==========================================================================
-- 入库单 #2 — 5 月 22 日新建，草稿状态（尚未 release）
-- ==========================================================================
INSERT INTO inbound_order (id, inbound_no, supplier_id, source_doc_no, status, remark, released_at) VALUES
(2, 'IN-20260522-001', 3, 'PO-20260518-002', 'DRAFT', '首批车身结构件到货预告', NULL);

INSERT INTO inbound_order_line (id, inbound_order_id, line_no, material_id, supplier_id, planned_qty, received_qty, target_warehouse_id, target_location_id, container_type_id) VALUES
(3, 2, 1, 3, 3, 600, 0, 1, 4, 3),   -- 前纵梁连接板 / EP-1200(500件/箱) → release后将生成 2 箱(500+100)
(4, 2, 2, 4, 3, 300, 0, 1, 5, 3);   -- 后轮罩内板 / EP-1200(500件/箱) → release后将生成 1 箱(300)

-- ==========================================================================
-- 入库单 #3 — 6 月 1 日到货，已释放全部待收
-- ==========================================================================
INSERT INTO inbound_order (id, inbound_no, supplier_id, source_doc_no, status, remark, released_at) VALUES
(3, 'IN-20260601-001', 2, 'PO-20260525-003', 'RELEASED', '仪表板横梁 + 加强件 批量到货', '2026-06-01 10:00:00');

INSERT INTO inbound_order_line (id, inbound_order_id, line_no, material_id, supplier_id, planned_qty, received_qty, target_warehouse_id, target_location_id, container_type_id) VALUES
(5, 3, 1, 5, 2, 120, 0, 1, 6, 2),   -- 仪表板横梁 / KLT-6422(50件/箱) → 3箱(50+50+20)
(6, 3, 2, 6, 3, 800, 0, 1, 4, 4);   -- 前纵梁加强件 / GLT-6010(200件/箱) → 4箱(200×4)

-- 行 1 库存标签（3 箱）：全部 PRINTED
INSERT INTO inventory_tag (id, inventory_tag_code, inbound_order_id, inbound_order_line_id, location_id, container_type_id, board_qty, status, printed_at) VALUES
(6,  'IT:v1:IN-20260601-001:1:1', 3, 5, 6, 2, 50, 'PRINTED', '2026-06-01 10:05:00'),
(7,  'IT:v1:IN-20260601-001:1:2', 3, 5, 6, 2, 50, 'PRINTED', '2026-06-01 10:05:00'),
(8,  'IT:v1:IN-20260601-001:1:3', 3, 5, 6, 2, 20, 'PRINTED', '2026-06-01 10:05:00');

-- 行 2 库存标签（4 箱）：全部 PRINTED
INSERT INTO inventory_tag (id, inventory_tag_code, inbound_order_id, inbound_order_line_id, location_id, container_type_id, board_qty, status, printed_at) VALUES
(9,  'IT:v1:IN-20260601-001:2:1', 3, 6, 4, 4, 200, 'PRINTED', '2026-06-01 10:05:00'),
(10, 'IT:v1:IN-20260601-001:2:2', 3, 6, 4, 4, 200, 'PRINTED', '2026-06-01 10:05:00'),
(11, 'IT:v1:IN-20260601-001:2:3', 3, 6, 4, 4, 200, 'PRINTED', '2026-06-01 10:05:00'),
(12, 'IT:v1:IN-20260601-001:2:4', 3, 6, 4, 4, 200, 'PRINTED', '2026-06-01 10:05:00');

-- ==========================================================================
-- 入库单 #4 — 6 月 10 日到货，已完成（全部入库）
-- ==========================================================================
INSERT INTO inbound_order (id, inbound_no, supplier_id, source_doc_no, status, remark, released_at, completed_at) VALUES
(4, 'IN-20260610-001', 1, 'PO-20260605-004', 'COMPLETED', '5 月余数补发 + 新 PO 首车', '2026-06-10 14:00:00', '2026-06-10 15:45:00');

INSERT INTO inbound_order_line (id, inbound_order_id, line_no, material_id, supplier_id, planned_qty, received_qty, target_warehouse_id, target_location_id, container_type_id) VALUES
(7, 4, 1, 1, 1, 200, 200, 1, 1, 1),   -- 前保险杠支架 / KLT-4320 → 2箱(100+100) 全收
(8, 4, 2, 2, 1, 150, 150, 1, 2, 1);   -- 加速踏板模块 / KLT-4320 → 2箱(100+50) 全收

-- 行 1 库存标签（2 箱）：全部 RECEIVED
INSERT INTO inventory_tag (id, inventory_tag_code, inbound_order_id, inbound_order_line_id, location_id, container_type_id, board_qty, status, printed_at, received_at) VALUES
(13, 'IT:v1:IN-20260610-001:1:1', 4, 7, 1, 1, 100, 'RECEIVED', '2026-06-10 14:05:00', '2026-06-10 15:20:00'),
(14, 'IT:v1:IN-20260610-001:1:2', 4, 7, 1, 1, 100, 'RECEIVED', '2026-06-10 14:05:00', '2026-06-10 15:22:00');

-- 行 2 库存标签（2 箱）：全部 RECEIVED
INSERT INTO inventory_tag (id, inventory_tag_code, inbound_order_id, inbound_order_line_id, location_id, container_type_id, board_qty, status, printed_at, received_at) VALUES
(15, 'IT:v1:IN-20260610-001:2:1', 4, 8, 2, 1, 100, 'RECEIVED', '2026-06-10 14:05:00', '2026-06-10 15:35:00'),
(16, 'IT:v1:IN-20260610-001:2:2', 4, 8, 2, 1, 50,  'RECEIVED', '2026-06-10 14:05:00', '2026-06-10 15:40:00');

-- movement 记录
INSERT INTO inventory_movement (id, movement_no, movement_type, source_type, source_id, inventory_tag_id, material_id, warehouse_id, storage_location_id, planned_location_id, qty, occurred_at, operator_name) VALUES
(5, 'MV-20260610152000-Q7R8S9T0', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 13, 13, 1, 1, 1, 1, 100, '2026-06-10 15:20:00', '李伟'),
(6, 'MV-20260610152200-U1V2W3X4', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 14, 14, 1, 1, 1, 1, 100, '2026-06-10 15:22:00', '李伟'),
(7, 'MV-20260610153500-Y5Z6A7B8', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 15, 15, 2, 1, 2, 2, 100, '2026-06-10 15:35:00', '李伟'),
(8, 'MV-20260610154000-C9D0E1F2', 'INBOUND_RECEIVE', 'INVENTORY_TAG', 16, 16, 2, 1, 2, 2, 50,  '2026-06-10 15:40:00', '李伟');

-- ==========================================================================
-- 库存余额（汇总已入库库存标签）
-- ==========================================================================
-- 前保险杠支架 / WH-JY / A-01：IN-20260520-001 行1(200件) + IN-20260610-001 行1(200件) = 400
INSERT INTO inventory_balance (material_id, warehouse_id, storage_location_id, on_hand_qty) VALUES
(1, 1, 1, 400);

-- 加速踏板模块 / WH-JY / A-02：IN-20260520-001 行2(180件) + IN-20260610-001 行2(150件) = 330
INSERT INTO inventory_balance (material_id, warehouse_id, storage_location_id, on_hand_qty) VALUES
(2, 1, 2, 330);

-- ==========================================================================
-- 出库单 #1 — 6 月 15 日，草稿（待组装线领料）
-- ==========================================================================
INSERT INTO outbound_order (id, outbound_no, supplier_id, purpose, source_doc_no, status, remark) VALUES
(1, 'OUT-20260615-001', 1, 'PRODUCTION_PICK', 'WO-20260615-001', 'DRAFT', '总装线 T2 工位 6/15 生产领料计划');

INSERT INTO outbound_order_line (id, outbound_order_id, line_no, material_id, supplier_id, planned_qty, picked_qty) VALUES
(1, 1, 1, 1, 1, 80,  0),
(2, 1, 2, 2, 1, 60,  0);
