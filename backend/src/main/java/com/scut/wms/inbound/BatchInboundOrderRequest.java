package com.scut.wms.inbound;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchInboundOrderRequest(
        @Size(max = 64, message = "来源单号不能超过 64 个字符")
        String sourceDocNo,

        @Size(max = 255, message = "备注不能超过 255 个字符")
        String remark,

        @Valid
        @NotEmpty(message = "批量入库明细不能为空")
        List<InboundOrderRequest.Line> lines
) {
}
