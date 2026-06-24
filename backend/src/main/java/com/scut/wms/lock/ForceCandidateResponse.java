package com.scut.wms.lock;

import java.math.BigDecimal;
import java.util.List;

public record ForceCandidateResponse(List<LineCandidates> lines) {
    public record LineCandidates(
            int lineNo,
            Long materialId,
            String materialCode,
            String materialName,
            BigDecimal plannedQty,
            List<InventoryTagEntry> inventoryTags
    ) {}

    public record InventoryTagEntry(
            Long inventoryTagId,
            String inventoryTagCode,
            String locationName,
            BigDecimal qty,
            boolean locked,
            String lockedByOutboundNo
    ) {}
}
