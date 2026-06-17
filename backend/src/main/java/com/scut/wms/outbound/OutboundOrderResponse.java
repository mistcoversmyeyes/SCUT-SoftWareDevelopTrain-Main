package com.scut.wms.outbound;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OutboundOrderResponse(
        Long id,
        String outboundNo,
        SupplierInfo supplier,
        String purpose,
        String sourceDocNo,
        String status,
        String remark,
        int lineCount,
        BigDecimal plannedQty,
        BigDecimal pickedQty,
        LocalDateTime releasedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        List<LineDisplay> lines
) {
    public record SupplierInfo(Long id, String code, String name) {
    }

    public record LineDisplay(
            Long id,
            int lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            SupplierInfo supplier,
            BigDecimal plannedQty,
            BigDecimal pickedQty,
            Long targetWarehouseId,
            String warehouseName,
            Long targetLocationId,
            String locationName,
            Long containerTypeId,
            String containerTypeName,
            BigDecimal capacityQty
    ) {
    }
}
