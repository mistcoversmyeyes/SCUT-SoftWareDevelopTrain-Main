package com.scut.wms.aiwarning;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InventoryFlowHistoryRecordView(
        Long batchId,
        Integer rowNumber,
        LocalDate businessDate,
        String materialCode,
        String warehouseCode,
        String locationCode,
        String boardCode,
        String movementType,
        BigDecimal quantity,
        String sourceOrderNo,
        String qualityStatus,
        LocalDateTime importedAt
) {
}
