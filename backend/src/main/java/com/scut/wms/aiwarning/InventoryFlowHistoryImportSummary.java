package com.scut.wms.aiwarning;

import java.time.LocalDate;
import java.util.Map;

public record InventoryFlowHistoryImportSummary(
        Integer materialCount,
        Map<String, Long> movementTypeCounts,
        LocalDate businessDateStart,
        LocalDate businessDateEnd
) {
}
