package com.scut.wms.outbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchOutboundOrderRequest(
        String purpose,

        @Size(max = 64, message = "来源单号不能超过 64 个字符")
        String sourceDocNo,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        @Valid
        @NotEmpty(message = "批量出库明细不能为空")
        List<OutboundOrderRequest.LineItem> lines
) {
}
