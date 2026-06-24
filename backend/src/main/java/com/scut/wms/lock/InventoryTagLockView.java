package com.scut.wms.lock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryTagLockView(
        Long lockId,
        String inventoryTagCode,
        BigDecimal boardQty,
        String inventoryTagStatus,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal lockQty,
        String lockStatus,
        String outboundNo,
        Long outboundOrderId,
        LocalDateTime createdAt
) {
}
