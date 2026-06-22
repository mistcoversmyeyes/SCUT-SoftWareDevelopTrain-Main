DROP TABLE IF EXISTS ai_inventory_flow_history;
DROP TABLE IF EXISTS ai_import_batch;
DROP TABLE IF EXISTS inventory_hold;
DROP TABLE IF EXISTS inventory_lock;
DROP TABLE IF EXISTS inventory_balance;
DROP TABLE IF EXISTS inventory_movement;
DROP TABLE IF EXISTS kanban_board;
DROP TABLE IF EXISTS outbound_order_line;
DROP TABLE IF EXISTS outbound_order;
DROP TABLE IF EXISTS inbound_order_line;
DROP TABLE IF EXISTS inbound_order;
DROP TABLE IF EXISTS material_container_type;
DROP TABLE IF EXISTS storage_location;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS material;
DROP TABLE IF EXISTS supplier;
DROP TABLE IF EXISTS container_type;

CREATE TABLE supplier (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  supplier_code VARCHAR(64) NOT NULL UNIQUE,
  supplier_name VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE container_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  container_code VARCHAR(64) NOT NULL UNIQUE,
  container_name VARCHAR(128) NOT NULL,
  capacity_qty DECIMAL(18, 3),
  status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_code VARCHAR(64) NOT NULL UNIQUE,
  material_name VARCHAR(128) NOT NULL,
  specification VARCHAR(128),
  unit VARCHAR(32) NOT NULL,
  supplier_id BIGINT,
  low_stock_qty DECIMAL(18, 3),
  high_stock_qty DECIMAL(18, 3),
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_material_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
);

CREATE TABLE material_container_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_id BIGINT NOT NULL,
  container_type_id BIGINT NOT NULL,
  is_default TINYINT DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_mct UNIQUE (material_id, container_type_id),
  CONSTRAINT fk_mct_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_mct_container FOREIGN KEY (container_type_id) REFERENCES container_type(id),
  INDEX idx_mct_material (material_id),
  INDEX idx_mct_container (container_type_id)
);

CREATE TABLE warehouse (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  warehouse_code VARCHAR(64) NOT NULL UNIQUE,
  warehouse_name VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE storage_location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  warehouse_id BIGINT NOT NULL,
  location_code VARCHAR(64) NOT NULL,
  location_name VARCHAR(128) NOT NULL,
  max_capacity DECIMAL(10,2) DEFAULT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_storage_location UNIQUE (warehouse_id, location_code),
  CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
);

CREATE TABLE inbound_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inbound_no VARCHAR(64) NOT NULL UNIQUE,
  supplier_id BIGINT DEFAULT NULL,
  source_doc_no VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  remark VARCHAR(255),
  released_at DATETIME,
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_inbound_order_status (status),
  INDEX idx_inbound_order_supplier_status (supplier_id, status)
);

CREATE TABLE inbound_order_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inbound_order_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  material_id BIGINT NOT NULL,
  supplier_id BIGINT DEFAULT NULL,
  planned_qty DECIMAL(18, 3) NOT NULL,
  received_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
  target_warehouse_id BIGINT NOT NULL,
  target_location_id BIGINT NOT NULL,
  container_type_id BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_inbound_order_line UNIQUE (inbound_order_id, line_no),
  CONSTRAINT fk_inbound_line_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_order(id),
  CONSTRAINT fk_inbound_line_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_inbound_line_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  CONSTRAINT fk_inbound_line_warehouse FOREIGN KEY (target_warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_inbound_line_location FOREIGN KEY (target_location_id) REFERENCES storage_location(id)
);

CREATE TABLE kanban_board (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  kanban_code VARCHAR(128) NOT NULL UNIQUE,
  inbound_order_id BIGINT NOT NULL,
  inbound_order_line_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL DEFAULT 0,
  container_type_id BIGINT NOT NULL DEFAULT 0,
  board_qty DECIMAL(18, 3) NOT NULL,
  picked_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  printed_at DATETIME,
  received_at DATETIME,
  locked_by_order_id BIGINT DEFAULT NULL,
  locked_by_order_line_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kanban_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_order(id),
  CONSTRAINT fk_kanban_line FOREIGN KEY (inbound_order_line_id) REFERENCES inbound_order_line(id),
  CONSTRAINT fk_kanban_location FOREIGN KEY (location_id) REFERENCES storage_location(id),
  CONSTRAINT fk_kanban_container FOREIGN KEY (container_type_id) REFERENCES container_type(id),
  INDEX idx_kanban_line_status (inbound_order_line_id, status),
  INDEX idx_kanban_location_status (location_id, status)
);

CREATE TABLE inventory_movement (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  movement_no VARCHAR(64) NOT NULL UNIQUE,
  movement_type VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id BIGINT,
  kanban_board_id BIGINT,
  material_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  storage_location_id BIGINT NOT NULL,
  planned_location_id BIGINT DEFAULT NULL,
  qty DECIMAL(18, 3) NOT NULL,
  occurred_at DATETIME NOT NULL,
  operator_name VARCHAR(64),
  outbound_order_id BIGINT DEFAULT NULL,
  outbound_order_line_id BIGINT DEFAULT NULL,
  force_outbound TINYINT(1) NOT NULL DEFAULT 0,
  force_remark VARCHAR(255) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_movement_kanban FOREIGN KEY (kanban_board_id) REFERENCES kanban_board(id),
  CONSTRAINT fk_movement_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_movement_location FOREIGN KEY (storage_location_id) REFERENCES storage_location(id),
  CONSTRAINT fk_movement_planned_location FOREIGN KEY (planned_location_id) REFERENCES storage_location(id),
  INDEX idx_movement_material_time (material_id, occurred_at),
  INDEX idx_movement_location_time (warehouse_id, storage_location_id, occurred_at)
);

CREATE TABLE inventory_balance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_id BIGINT NOT NULL,
  warehouse_id BIGINT NOT NULL,
  storage_location_id BIGINT NOT NULL,
  on_hand_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_inventory_balance UNIQUE (material_id, warehouse_id, storage_location_id),
  CONSTRAINT fk_balance_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_balance_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_balance_location FOREIGN KEY (storage_location_id) REFERENCES storage_location(id)
);

CREATE TABLE outbound_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  outbound_no VARCHAR(64) NOT NULL UNIQUE,
  supplier_id BIGINT DEFAULT NULL,
  purpose VARCHAR(64),
  source_doc_no VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  qrcode VARCHAR(255) DEFAULT NULL,
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
  supplier_id BIGINT DEFAULT NULL,
  planned_qty DECIMAL(18, 3) NOT NULL,
  picked_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
  target_warehouse_id BIGINT DEFAULT NULL,
  target_location_id BIGINT DEFAULT NULL,
  container_type_id BIGINT DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_outbound_order_line UNIQUE (outbound_order_id, line_no),
  CONSTRAINT fk_outbound_line_order FOREIGN KEY (outbound_order_id) REFERENCES outbound_order(id),
  CONSTRAINT fk_outbound_line_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_outbound_line_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
);

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

CREATE TABLE inventory_hold (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  kanban_board_id BIGINT NOT NULL,
  hold_type VARCHAR(32) NOT NULL,
  hold_qty DECIMAL(18, 3) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  reason VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  operator_name VARCHAR(64) NOT NULL,
  released_reason VARCHAR(128) DEFAULT NULL,
  released_remark VARCHAR(255) DEFAULT NULL,
  released_by VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  released_at DATETIME DEFAULT NULL,
  CONSTRAINT fk_hold_kanban FOREIGN KEY (kanban_board_id) REFERENCES kanban_board(id),
  INDEX idx_hold_kanban_status (kanban_board_id, status),
  INDEX idx_hold_type_status (hold_type, status)
);

CREATE TABLE ai_import_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  import_type VARCHAR(64) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  template_version VARCHAR(64) NOT NULL,
  total_rows INT NOT NULL DEFAULT 0,
  success_rows INT NOT NULL DEFAULT 0,
  failed_rows INT NOT NULL DEFAULT 0,
  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_import_type_time (import_type, imported_at)
);

CREATE TABLE ai_inventory_flow_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  import_row_no INT NOT NULL,
  business_date DATE NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  warehouse_code VARCHAR(64) NOT NULL,
  location_code VARCHAR(64) NOT NULL,
  board_code VARCHAR(128) NOT NULL,
  movement_type VARCHAR(32) NOT NULL,
  quantity DECIMAL(18, 3) NOT NULL,
  source_order_no VARCHAR(64) NOT NULL,
  quality_status VARCHAR(32),
  imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_flow_batch FOREIGN KEY (batch_id) REFERENCES ai_import_batch(id),
  INDEX idx_ai_flow_batch_row (batch_id, import_row_no),
  INDEX idx_ai_flow_material_date (material_code, business_date),
  INDEX idx_ai_flow_movement_date (movement_type, business_date)
);
