package com.scut.wms.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryTransactionMapper {
    ScanKanbanContext selectScanKanbanForUpdate(@Param("kanbanCode") String kanbanCode);

    InventoryBalance selectBalanceForUpdate(
            @Param("materialId") Long materialId,
            @Param("warehouseId") Long warehouseId,
            @Param("storageLocationId") Long storageLocationId
    );
}
