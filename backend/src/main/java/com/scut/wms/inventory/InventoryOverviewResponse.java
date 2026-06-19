package com.scut.wms.inventory;

import java.math.BigDecimal;
import java.util.List;

public record InventoryOverviewResponse(
        List<WarehouseOverview> warehouses,
        List<SupplierMaterialOverview> suppliers
) {
    public record WarehouseOverview(
            Long id,
            String code,
            String name,
            List<LocationSlot> locations
    ) {}

    public record LocationSlot(
            Long id,
            Long warehouseId,
            String code,
            String name,
            BigDecimal maxCapacity,
            int usedBoxes,
            BigDecimal totalPieces
    ) {}

    public record SupplierMaterialOverview(
            Long id,
            String code,
            String name,
            List<MaterialStock> materials
    ) {}

    public record MaterialStock(
            Long id,
            String code,
            String name,
            BigDecimal highStockQty,
            BigDecimal currentQty
    ) {}
}
