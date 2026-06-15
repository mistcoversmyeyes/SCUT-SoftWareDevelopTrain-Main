package com.scut.wms.outbound;

import java.time.LocalDateTime;
import java.util.List;

public record OutboundPrintResponse(
        Long id,
        String outboundNo,
        String supplierCode,
        String supplierName,
        String purpose,
        String sourceDocNo,
        String status,
        String remark,
        LocalDateTime releasedAt,
        List<OutboundPrintLine> lines
) {
}
