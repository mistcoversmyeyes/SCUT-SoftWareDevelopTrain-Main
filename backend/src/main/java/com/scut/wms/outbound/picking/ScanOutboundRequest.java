package com.scut.wms.outbound.picking;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record ScanOutboundRequest(
        @NotBlank(message = "库存标签编码不能为空")
        String inventoryTagCode,
        BigDecimal qty,
        Long outboundOrderId,
        Long outboundOrderLineId,
        Boolean confirmNonRecommended,
        Boolean confirmNonFifo
) {
    public boolean isConfirmNonRecommended() {
        return Boolean.TRUE.equals(confirmNonRecommended);
    }

    public boolean isConfirmNonFifo() {
        return Boolean.TRUE.equals(confirmNonFifo);
    }
}
