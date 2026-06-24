package com.scut.wms.inventory;

import jakarta.validation.constraints.NotBlank;

public record ScanInboundRequest(
        @NotBlank(message = "库存标签码不能为空")
        String inventoryTagCode,
        Long locationId
) {
}
