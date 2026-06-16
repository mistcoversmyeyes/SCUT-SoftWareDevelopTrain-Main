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
            List<KanbanEntry> kanbans
    ) {}

    public record KanbanEntry(
            Long kanbanBoardId,
            String kanbanCode,
            String locationName,
            BigDecimal qty,
            boolean locked,
            String lockedByOutboundNo
    ) {}
}
