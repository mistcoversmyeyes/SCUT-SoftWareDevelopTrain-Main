package com.scut.wms.outbound;

import java.math.BigDecimal;

public record OutboundPrintLine(
        Integer lineNo,
        String materialCode,
        String materialName,
        BigDecimal plannedQty,
        BigDecimal pickedQty
) {
}
