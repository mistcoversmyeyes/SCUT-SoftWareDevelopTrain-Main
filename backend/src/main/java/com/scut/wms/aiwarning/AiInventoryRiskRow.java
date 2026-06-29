package com.scut.wms.aiwarning;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AiInventoryRiskRow(
        String materialCode,
        String materialName,
        String warehouseCode,
        String locationCode,
        BigDecimal onHandQty,
        BigDecimal availableQty,
        BigDecimal lockedQty,
        BigDecimal sealedQty,
        BigDecimal avgDailyOutbound7d,
        BigDecimal avgDailyOutbound30d,
        BigDecimal daysOfCover,
        LocalDate lastInboundDate,
        LocalDate lastOutboundDate,
        Integer inventoryAgeDays,
        Integer daysSinceLastOutbound,
        String latestQualityStatus,
        AiInventoryRiskLevel shortageRisk,
        AiInventoryRiskLevel stagnationRisk,
        AiInventoryRiskLevel qualityRisk
) {
}
