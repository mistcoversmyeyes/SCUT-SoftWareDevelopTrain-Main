package com.scut.wms.inventory;

import jakarta.validation.constraints.NotBlank;

public record ScanInboundRequest(
        @NotBlank(message = "看板码不能为空")
        String kanbanCode
) {
}
