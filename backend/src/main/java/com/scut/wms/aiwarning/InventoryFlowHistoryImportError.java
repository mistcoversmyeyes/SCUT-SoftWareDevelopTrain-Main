package com.scut.wms.aiwarning;

public record InventoryFlowHistoryImportError(
        Integer rowNumber,
        String field,
        String message,
        String rejectedValue
) {
}
