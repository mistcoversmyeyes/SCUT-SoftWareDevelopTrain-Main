package com.scut.wms.inbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryTagPrintResponse(
        Long inventoryTagId,
        Long inboundOrderId,
        String inventoryTagCode,
        String inboundNo,
        Integer lineNo,
        String supplierCode,
        String supplierName,
        String materialCode,
        String materialName,
        Long locationId,
        String locationName,
        BigDecimal qty,
        BigDecimal pickedQty,
        BigDecimal availableQty,
        String status,
        String activeHoldType,
        String activeHoldReason,
        LocalDateTime printedAt,
        String containerTypeName,
        BigDecimal capacityQty
) {
}
