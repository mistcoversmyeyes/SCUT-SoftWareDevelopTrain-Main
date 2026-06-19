package com.scut.wms.outbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record OutboundOrderRequest(
        String purpose,

        String sourceDocNo,

        String remark,

        @Valid
        @NotEmpty(message = "出库单明细不能为空")
        List<LineItem> lines
) {
    public record LineItem(
            @NotNull(message = "供应商不能为空")
            Long supplierId,

            @NotNull(message = "物料不能为空")
            Long materialId,

            @NotNull(message = "计划数量不能为空")
            @DecimalMin(value = "0.000", inclusive = false, message = "计划数量必须大于 0")
            BigDecimal plannedQty,

            Long targetWarehouseId,

            Long targetLocationId,

            Long containerTypeId
    ) {
    }
}
