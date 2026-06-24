package com.scut.wms.lock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InventoryHoldView(
        Long holdId,
        Long inventoryTagId,
        String inventoryTagCode,
        String materialCode,
        String materialName,
        String locationName,
        String inventoryTagStatus,
        String holdType,
        BigDecimal holdQty,
        String status,
        String reason,
        String remark,
        String operatorName,
        String releasedReason,
        String releasedRemark,
        String releasedBy,
        LocalDateTime createdAt,
        LocalDateTime releasedAt
) {
}
