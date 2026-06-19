package com.scut.wms.inbound;

import java.math.BigDecimal;

public record InboundPrintLine(
        Integer lineNo,
        String materialCode,
        String materialName,
        BigDecimal plannedQty,
        BigDecimal receivedQty,
        String warehouseName,
        String locationName
) {
}
