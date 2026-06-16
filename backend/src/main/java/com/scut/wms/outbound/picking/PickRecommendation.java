package com.scut.wms.outbound.picking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PickRecommendation(
        String kanbanCode,
        String warehouseName,
        String locationName,
        BigDecimal availableQty,
        LocalDateTime receivedAt
) {
}
