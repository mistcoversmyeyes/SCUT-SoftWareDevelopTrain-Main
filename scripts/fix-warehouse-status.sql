-- 修复仓库/库位状态值：ACTIVE→ENABLED, INACTIVE→DISABLED
-- 在 Ubuntu VM 中执行：
--   cd <项目路径>
--   docker-compose exec mysql mysql -uroot -proot scut_wms < scripts/fix-warehouse-status.sql

UPDATE warehouse SET status = 'ENABLED' WHERE status = 'ACTIVE';
UPDATE storage_location SET status = 'ENABLED' WHERE status = 'ACTIVE';
UPDATE warehouse SET status = 'DISABLED' WHERE status = 'INACTIVE';
UPDATE storage_location SET status = 'DISABLED' WHERE status = 'INACTIVE';

-- 恢复种子仓库名称：精英仓库 → 吉耀仓
UPDATE warehouse SET warehouse_name = '吉耀仓' WHERE warehouse_code = 'WH-JY';

-- 验证
SELECT id, warehouse_code, warehouse_name, status FROM warehouse;
SELECT id, location_code, location_name, status FROM storage_location;
