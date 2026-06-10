package com.scut.wms.inbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InboundOrderResponse(
        Long id,
        String inboundNo,
        SupplierDisplay supplier,
        String sourceDocNo,
        String status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime releasedAt,
        LocalDateTime completedAt,
        int lineCount,
        BigDecimal plannedQty,
        BigDecimal receivedQty,
        List<LineDisplay> lines
) {
    public record SupplierDisplay(Long id, String code, String name) {
    }

    public record LineDisplay(
            Long id,
            Integer lineNo,
            Long materialId,
            BigDecimal plannedQty,
            BigDecimal receivedQty,
            Long targetWarehouseId,
            Long targetLocationId
    ) {
    }
}
