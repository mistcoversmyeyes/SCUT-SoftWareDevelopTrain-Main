package com.scut.wms.lock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LockOrderSummary(
        Long outboundOrderId,
        String outboundNo,
        String supplierName,
        String status,
        BigDecimal totalLockQty,
        BigDecimal totalPickedQty,
        boolean hasForceStolen,
        LocalDateTime createdAt
) {}
