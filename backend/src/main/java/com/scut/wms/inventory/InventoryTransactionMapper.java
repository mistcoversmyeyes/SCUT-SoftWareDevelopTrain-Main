package com.scut.wms.inventory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import com.scut.wms.outbound.picking.FifoPickCandidate;
import com.scut.wms.outbound.picking.PickRecommendation;

@Mapper
public interface InventoryTransactionMapper {
    ScanInventoryTagContext selectScanInventoryTagForUpdate(@Param("inventoryTagCode") String inventoryTagCode);

    ScanInventoryTagContext selectInventoryTagContext(@Param("inventoryTagCode") String inventoryTagCode);

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
            @Param("inventoryTagCode") String inventoryTagCode
    );

    InventoryTagTraceView selectInventoryTagTrace(@Param("inventoryTagCode") String inventoryTagCode);

    List<FifoPickCandidate> selectFifoCandidateForUpdate(
            @Param("materialId") Long materialId,
            @Param("warehouseId") Long warehouseId,
            @Param("storageLocationId") Long storageLocationId
    );

    List<PickRecommendation> selectFifoRecommendations(
            @Param("materialId") Long materialId,
            @Param("warehouseIds") List<Long> warehouseIds
    );

    List<FifoPickCandidate> selectFifoCandidatesForLock(
            @Param("materialId") Long materialId,
            @Param("warehouseIds") List<Long> warehouseIds,
            @Param("containerTypeId") Long containerTypeId
    );

    Long selectEarliestFifoTagId(@Param("materialId") Long materialId);
}