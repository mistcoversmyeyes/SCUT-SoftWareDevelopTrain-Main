package com.scut.wms.outbound.picking;

import jakarta.validation.constraints.NotBlank;

public record ScanOutboundRequest(
        @NotBlank(message = "看板编码不能为空")
        String kanbanCode
) {
}
