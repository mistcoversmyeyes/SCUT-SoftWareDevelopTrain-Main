package com.scut.wms.lock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LockDetailView(
        Long lockId,
        String kanbanCode,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal lockQty,
        String lockStatus,
        Long lineNo,
        String stolenByOutboundNo,
        LocalDateTime stolenAt,
        LocalDateTime unlockedAt,
        String unlockedBy
) {}
