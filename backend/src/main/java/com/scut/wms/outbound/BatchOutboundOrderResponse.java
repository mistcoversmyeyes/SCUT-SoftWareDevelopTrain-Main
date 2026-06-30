package com.scut.wms.outbound;

import java.util.List;

public record BatchOutboundOrderResponse(
        int orderCount,
        int lineCount,
        List<OutboundOrderResponse> orders
) {
}
