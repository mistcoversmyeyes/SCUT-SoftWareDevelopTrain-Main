package com.scut.wms.outbound.picking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record RecommendRequest(
        @NotNull Long materialId,
        @NotEmpty List<Long> warehouseIds,
        @NotNull @DecimalMin("0.001") BigDecimal neededQty
) {
}
