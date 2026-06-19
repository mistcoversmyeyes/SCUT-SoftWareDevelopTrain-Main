package com.scut.wms.inbound;

import java.time.LocalDateTime;
import java.util.List;

public record InboundPrintResponse(
        Long id,
        String inboundNo,
        String supplierCode,
        String supplierName,
        String sourceDocNo,
        String status,
        String remark,
        LocalDateTime releasedAt,
        List<InboundPrintLine> lines
) {
}
