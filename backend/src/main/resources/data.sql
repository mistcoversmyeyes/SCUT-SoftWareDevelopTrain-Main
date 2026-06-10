INSERT IGNORE INTO supplier (id, supplier_code, supplier_name, contact_name, contact_phone, status)
VALUES
  (1, '8KH', '佛山华翔金属件 8KH', '张工', '13800000001', 'ENABLED'),
  (2, '4MU', '宁波劳伦斯 4MU', '李工', '13800000002', 'ENABLED');

INSERT IGNORE INTO material (id, material_code, material_name, specification, unit, supplier_id, status)
VALUES
  (1, '5HG 807 109 C', '前保险杠支架', '汽车零件', '件', 1, 'ENABLED'),
  (2, '5WD 723 913 C', '踏板组件', '汽车零件', '件', 1, 'ENABLED'),
  (3, '5Q0 803 219 D', '车身连接件', '汽车零件', '件', 2, 'ENABLED');

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
  id, inbound_order_id, line_no, material_id, planned_qty, received_qty, target_warehouse_id, target_location_id
)
VALUES
  (1, 1, 1, 1, 120.000, 0.000, 1, 1),
  (2, 1, 2, 2, 80.000, 0.000, 1, 2);

INSERT IGNORE INTO kanban_board (
  id, kanban_code, inbound_order_id, inbound_order_line_id, board_qty, status, printed_at
)
VALUES
  (1, 'KB:v1:IN-20260610-001:1:1', 1, 1, 120.000, 'PRINTED', '2026-06-10 09:05:00'),
  (2, 'KB:v1:IN-20260610-001:2:1', 1, 2, 80.000, 'PRINTED', '2026-06-10 09:05:00');
