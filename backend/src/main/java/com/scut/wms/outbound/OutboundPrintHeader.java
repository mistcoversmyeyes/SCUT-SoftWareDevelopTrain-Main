package com.scut.wms.outbound;

import java.time.LocalDateTime;

public record OutboundPrintHeader(
        Long id,
        String outboundNo,
        String supplierCode,
        String supplierName,
        String purpose,
        String sourceDocNo,
        String status,
        String remark,
        LocalDateTime releasedAt
) {
}
