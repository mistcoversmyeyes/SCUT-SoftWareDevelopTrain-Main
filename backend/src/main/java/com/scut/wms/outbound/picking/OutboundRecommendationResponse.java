package com.scut.wms.outbound.picking;

import java.util.List;

public record OutboundRecommendationResponse(
        Long outboundOrderId,
        String outboundNo,
        List<OutboundRecommendationLine> lines
) {
}
