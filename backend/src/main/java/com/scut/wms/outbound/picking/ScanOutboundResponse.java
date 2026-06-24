package com.scut.wms.outbound.picking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ScanOutboundResponse(
        String inventoryTagCode,
        String materialCode,
        String materialName,
        String locationName,
        BigDecimal pickedQty,
        String newInventoryTagStatus,
        LocalDateTime occurredAt,
        Long outboundOrderId,
        Long outboundOrderLineId,
        String outboundNo,
        String orderStatus
) {}
