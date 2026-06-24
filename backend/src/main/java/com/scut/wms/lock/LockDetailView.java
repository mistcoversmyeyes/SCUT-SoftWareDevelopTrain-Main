package com.scut.wms.lock;

import java.math.BigDecimal;

public record LockDetailView(
        Long lockId,
        String inventoryTagCode,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal lockQty,
        String lockStatus,
        Long lineNo
) {}
