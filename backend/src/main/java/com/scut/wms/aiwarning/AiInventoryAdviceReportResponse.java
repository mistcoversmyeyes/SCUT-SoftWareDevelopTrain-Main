package com.scut.wms.aiwarning;

import java.time.LocalDateTime;

public record AiInventoryAdviceReportResponse(
        String status,
        boolean configured,
        String provider,
        String model,
        LocalDateTime generatedAt,
        AiInventoryRiskSummary summary,
        String reportMarkdown,
        String message
) {
}
