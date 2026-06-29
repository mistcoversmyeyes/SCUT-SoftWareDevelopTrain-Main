package com.scut.wms.aiwarning;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AiInventoryRiskAnalysisResponse(
        String direction,
        String readinessCode,
        String readinessLabel,
        String readinessReason,
        LocalDate snapshotDate,
        LocalDateTime generatedAt,
        AiInventoryRiskSummary summary,
        List<AiInventoryRiskRow> rows
) {
}
