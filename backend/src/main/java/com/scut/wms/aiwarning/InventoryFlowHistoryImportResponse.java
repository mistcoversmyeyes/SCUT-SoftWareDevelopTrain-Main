package com.scut.wms.aiwarning;

import java.util.List;

public record InventoryFlowHistoryImportResponse(
        String importObject,
        String fileName,
        Integer totalRows,
        Integer successRows,
        Integer failedRows,
        Long batchId,
        InventoryFlowHistoryImportSummary summary,
        List<InventoryFlowHistoryImportError> errors
) {
}
