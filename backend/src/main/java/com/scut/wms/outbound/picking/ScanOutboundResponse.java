package com.scut.wms.outbound.picking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScanOutboundResponse(
        String kanbanCode,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal pickedQty,
        String newKanbanStatus,
        LocalDateTime occurredAt,
        Long outboundOrderId,
        Long outboundOrderLineId,
        String orderStatus
) {
}
