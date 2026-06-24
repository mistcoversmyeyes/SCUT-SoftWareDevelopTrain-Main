package com.scut.wms.lock;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ForceLogView(
        String inventoryTagCode,
        String materialCode,
        String materialName,
        BigDecimal qty,
        String originalOutboundNo,
        String stolenByOutboundNo,
        LocalDateTime stolenAt
) {}
