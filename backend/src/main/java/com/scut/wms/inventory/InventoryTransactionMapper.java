package com.scut.wms.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryTransactionMapper {
    ScanKanbanContext selectScanKanbanForUpdate(@Param("kanbanCode") String kanbanCode);

    InventoryBalance selectBalanceForUpdate(
            @Param("materialId") Long materialId,
            @Param("warehouseId") Long warehouseId,
            @Param("storageLocationId") Long storageLocationId
    );

    List<InventoryBalanceView> selectInventoryBalances(
            @Param("materialCode") String materialCode,
            @Param("warehouseCode") String warehouseCode,
            @Param("locationCode") String locationCode
    );

    List<InventoryMovementView> selectInventoryMovements(
            @Param("materialCode") String materialCode,
            @Param("warehouseCode") String warehouseCode,
            @Param("locationCode") String locationCode,
            @Param("inboundNo") String inboundNo,
            @Param("kanbanCode") String kanbanCode
    );

    KanbanTraceView selectKanbanTrace(@Param("kanbanCode") String kanbanCode);
}
