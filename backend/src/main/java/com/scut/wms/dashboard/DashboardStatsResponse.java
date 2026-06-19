package com.scut.wms.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsResponse(
        long todayInboundCount,
        long todayOutboundCount,
        long totalMaterials,
        long pendingOrders,
        List<LowStockAlert> lowStockAlerts,
        List<HighStockAlert> highStockAlerts
) {
    public record LowStockAlert(
            String materialCode,
            String materialName,
            String warehouseCode,
            String warehouseName,
            String locationCode,
            String locationName,
            BigDecimal onHandQty,
            BigDecimal lowStockQty
    ) {
    }

    public record HighStockAlert(
            String materialCode,
            String materialName,
            String warehouseCode,
            String warehouseName,
            String locationCode,
            String locationName,
            BigDecimal onHandQty,
            BigDecimal highStockQty
    ) {
    }
}
