package com.scut.wms.inbound;

import java.util.List;

public record BatchInboundOrderResponse(
        int orderCount,
        int lineCount,
        List<InboundOrderResponse> orders
) {
}
