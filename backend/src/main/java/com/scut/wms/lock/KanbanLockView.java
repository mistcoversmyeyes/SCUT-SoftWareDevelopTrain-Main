package com.scut.wms.lock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record KanbanLockView(
        Long lockId,
        String kanbanCode,
        BigDecimal boardQty,
        String kanbanStatus,
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
