package com.scut.wms.outbound.picking;

import java.math.BigDecimal;
import java.util.List;

public record OutboundRecommendationLine(
        Long outboundOrderLineId,
        Integer lineNo,
        Long materialId,
        String materialCode,
        String materialName,
        BigDecimal neededQty,
        List<PickRecommendation> recommendations
) {
}
