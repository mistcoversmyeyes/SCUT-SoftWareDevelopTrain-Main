DROP TABLE IF EXISTS inventory_balance;
DROP TABLE IF EXISTS inventory_movement;
DROP TABLE IF EXISTS kanban_board;
DROP TABLE IF EXISTS inbound_order_line;
DROP TABLE IF EXISTS inbound_order;
DROP TABLE IF EXISTS storage_location;
DROP TABLE IF EXISTS warehouse;
DROP TABLE IF EXISTS material;
DROP TABLE IF EXISTS supplier;

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

CREATE TABLE material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_code VARCHAR(64) NOT NULL UNIQUE,
  material_name VARCHAR(128) NOT NULL,
  specification VARCHAR(128),
  unit VARCHAR(32) NOT NULL,
  supplier_id BIGINT,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_material_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id)
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
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_storage_location UNIQUE (warehouse_id, location_code),
  CONSTRAINT fk_location_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id)
);

CREATE TABLE inbound_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inbound_no VARCHAR(64) NOT NULL UNIQUE,
  supplier_id BIGINT NOT NULL,
  source_doc_no VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  remark VARCHAR(255),
  released_at DATETIME,
  completed_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_inbound_order_supplier FOREIGN KEY (supplier_id) REFERENCES supplier(id),
  INDEX idx_inbound_order_status (status),
  INDEX idx_inbound_order_supplier_status (supplier_id, status)
);

CREATE TABLE inbound_order_line (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inbound_order_id BIGINT NOT NULL,
  line_no INT NOT NULL,
  material_id BIGINT NOT NULL,
  planned_qty DECIMAL(18, 3) NOT NULL,
  received_qty DECIMAL(18, 3) NOT NULL DEFAULT 0,
  target_warehouse_id BIGINT NOT NULL,
  target_location_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uk_inbound_order_line UNIQUE (inbound_order_id, line_no),
  CONSTRAINT fk_inbound_line_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_order(id),
  CONSTRAINT fk_inbound_line_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_inbound_line_warehouse FOREIGN KEY (target_warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_inbound_line_location FOREIGN KEY (target_location_id) REFERENCES storage_location(id)
);

CREATE TABLE kanban_board (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  kanban_code VARCHAR(128) NOT NULL UNIQUE,
  inbound_order_id BIGINT NOT NULL,
  inbound_order_line_id BIGINT NOT NULL,
  board_qty DECIMAL(18, 3) NOT NULL,
  status VARCHAR(32) NOT NULL,
  printed_at DATETIME,
  received_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_kanban_order FOREIGN KEY (inbound_order_id) REFERENCES inbound_order(id),
  CONSTRAINT fk_kanban_line FOREIGN KEY (inbound_order_line_id) REFERENCES inbound_order_line(id),
  INDEX idx_kanban_line_status (inbound_order_line_id, status)
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
  qty DECIMAL(18, 3) NOT NULL,
  occurred_at DATETIME NOT NULL,
  operator_name VARCHAR(64),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_movement_kanban FOREIGN KEY (kanban_board_id) REFERENCES kanban_board(id),
  CONSTRAINT fk_movement_material FOREIGN KEY (material_id) REFERENCES material(id),
  CONSTRAINT fk_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse(id),
  CONSTRAINT fk_movement_location FOREIGN KEY (storage_location_id) REFERENCES storage_location(id),
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
