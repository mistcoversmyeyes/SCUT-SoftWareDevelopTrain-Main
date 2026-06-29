package com.scut.wms.aiwarning;

public record AiInventoryRiskSummary(
        int materialLocationCount,
        int shortageHighCount,
        int shortageCriticalCount,
        int stagnationHighCount,
        int qualityHighCount,
        int qualityExpiredCount,
        int dataUnpreparedCount
) {
}
