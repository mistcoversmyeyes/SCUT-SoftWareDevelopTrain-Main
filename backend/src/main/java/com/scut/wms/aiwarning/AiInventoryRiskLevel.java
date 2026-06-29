package com.scut.wms.aiwarning;

public record AiInventoryRiskLevel(
        String code,
        String label,
        String tone,
        String reason
) {
}
