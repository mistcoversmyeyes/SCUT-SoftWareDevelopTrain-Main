package com.scut.wms.aiwarning;

import java.time.LocalDateTime;

public record InventoryFlowHistoryBatchView(
        Long batchId,
        String importObject,
        String fileName,
        Integer totalRows,
        Integer successRows,
        Integer failedRows,
        LocalDateTime importedAt
) {
}
