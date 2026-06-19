package com.scut.wms.lock;

import java.math.BigDecimal;

public record LockDetailView(
        Long lockId,
        String kanbanCode,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal lockQty,
        String lockStatus,
        Long lineNo
) {}
