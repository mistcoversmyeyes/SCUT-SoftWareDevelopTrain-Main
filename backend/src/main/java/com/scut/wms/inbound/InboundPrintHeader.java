package com.scut.wms.inbound;

import java.time.LocalDateTime;

public record InboundPrintHeader(
        Long id,
        String inboundNo,
        String supplierCode,
        String supplierName,
        String sourceDocNo,
        String status,
        String remark,
        LocalDateTime releasedAt
) {
}
