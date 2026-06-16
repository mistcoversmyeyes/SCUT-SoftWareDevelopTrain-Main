INSERT IGNORE INTO container_type (id, container_code, container_name, capacity_qty, status)
VALUES
  (1, 'KLT-4320', '蓝色周转箱 KLT-4320', 100, 'ENABLED'),
  (2, 'EP-1200', '灰色托盘 EP-1200', 500, 'ENABLED');

INSERT IGNORE INTO supplier (id, supplier_code, supplier_name, contact_name, contact_phone, status)
VALUES
  (1, '8KH', '佛山华翔金属件 8KH', '张工', '13800000001', 'ENABLED'),
  (2, '4MU', '宁波劳伦斯 4MU', '李工', '13800000002', 'ENABLED');

INSERT IGNORE INTO material (id, material_code, material_name, specification, unit, supplier_id, low_stock_qty, high_stock_qty, status)
VALUES
  (1, '5HG 807 109 C', '前保险杠支架', '汽车零件', '件', 1, 10, 500, 'ENABLED'),
  (2, '5WD 723 913 C', '踏板组件', '汽车零件', '件', 1, 20, 400, 'ENABLED'),
  (3, '5Q0 803 219 D', '车身连接件', '汽车零件', '件', 2, 30, 600, 'ENABLED');

INSERT IGNORE INTO material_container_type (material_id, container_type_id, is_default)
VALUES
  (1, 1, 1),
  (2, 1, 1),
  (3, 2, 1);

INSERT IGNORE INTO warehouse (id, warehouse_code, warehouse_name, status)
VALUES (1, 'WH-JY', '吉耀仓', 'ENABLED');

INSERT IGNORE INTO storage_location (id, warehouse_id, location_code, location_name, status)
VALUES
  (1, 1, 'A-01', 'A区 01 库位', 'ENABLED'),
  (2, 1, 'A-02', 'A区 02 库位', 'ENABLED'),
  (3, 1, 'B-01', 'B区 01 库位', 'ENABLED');

INSERT IGNORE INTO inbound_order (id, inbound_no, supplier_id, source_doc_no, status, remark, released_at)
VALUES
  (1, 'IN-20260610-001', 1, 'PO-20260610-001', 'RELEASED', 'Week 2 采购入库演示单据', '2026-06-10 09:00:00');

INSERT IGNORE INTO inbound_order_line (
  id, inbound_order_id, line_no, material_id, supplier_id, planned_qty, received_qty, target_warehouse_id, target_location_id, container_type_id
)
VALUES
  (1, 1, 1, 1, 1, 120.000, 0.000, 1, 1, 1),
  (2, 1, 2, 2, 1, 80.000, 0.000, 1, 2, 1);

INSERT IGNORE INTO kanban_board (
  id, kanban_code, inbound_order_id, inbound_order_line_id, board_qty, status, printed_at, location_id, container_type_id
)
VALUES
  (1, 'KB:v1:IN-20260610-001:1:1', 1, 1, 120.000, 'PRINTED', '2026-06-10 09:05:00', 1, 1),
  (2, 'KB:v1:IN-20260610-001:2:1', 1, 2, 80.000, 'PRINTED', '2026-06-10 09:05:00', 2, 1);

-- Fix existing records that may have default values from previous schema version
UPDATE kanban_board SET location_id = 1, container_type_id = 1 WHERE id = 1 AND location_id = 0;
UPDATE kanban_board SET location_id = 2, container_type_id = 1 WHERE id = 2 AND location_id = 0;
UPDATE inbound_order_line SET container_type_id = 1 WHERE id IN (1, 2) AND container_type_id = 0;

INSERT IGNORE INTO outbound_order (id, outbound_no, supplier_id, purpose, source_doc_no, status, remark, released_at)
VALUES
  (1, 'OUT-20260615-001', 1, 'PRODUCTION_PICK', 'WO-20260615-001', 'DRAFT', 'Week 3 出库演示单据', NULL);

INSERT IGNORE INTO outbound_order_line (
  id, outbound_order_id, line_no, material_id, supplier_id, planned_qty, picked_qty
)
VALUES
  (1, 1, 1, 1, 1, 50.000, 0.000),
  (2, 1, 2, 2, 1, 30.000, 0.000);
