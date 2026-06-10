package com.scut.wms.inbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KanbanPrintResponse(
        String kanbanCode,
        String inboundNo,
        String supplierCode,
        String supplierName,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal qty,
        String status,
        LocalDateTime printedAt
) {
}
