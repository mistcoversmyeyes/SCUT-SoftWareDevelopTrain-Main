package com.scut.wms.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record InboundOrderRequest(
        @NotNull(message = "供应商不能为空")
        Long supplierId,

        @Size(max = 64, message = "来源单号不能超过 64 个字符")
        String sourceDocNo,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        @Valid
        @NotEmpty(message = "入库单明细不能为空")
        List<Line> lines
) {
    public record Line(
            @NotNull(message = "物料不能为空")
            Long materialId,

            @NotNull(message = "计划数量不能为空")
            @DecimalMin(value = "0.000", inclusive = false, message = "计划数量必须大于 0")
            BigDecimal plannedQty,

            @NotNull(message = "目标仓库不能为空")
            Long targetWarehouseId,

            @NotNull(message = "目标库位不能为空")
            Long targetLocationId
    ) {
    }
}
