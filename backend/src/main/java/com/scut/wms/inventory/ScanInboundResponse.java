package com.scut.wms.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScanInboundResponse(
        String kanbanCode,
        String inboundNo,
        String materialCode,
        String materialName,
        BigDecimal receivedQty,
        String locationName,
        String orderStatus,
        LocalDateTime receivedAt
) {
}
