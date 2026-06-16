package com.scut.wms.outbound.picking;

import com.scut.wms.inventory.ScanKanbanContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outbound")
public class PickingController {
    private final OutboundPickingService outboundPickingService;

    public PickingController(OutboundPickingService outboundPickingService) {
        this.outboundPickingService = outboundPickingService;
    }

    @GetMapping("/kanban-lookup")
    public ScanKanbanContext lookupKanban(@RequestParam String kanbanCode) {
        return outboundPickingService.lookupKanban(kanbanCode);
    }

    @PostMapping("/scan")
    public ScanOutboundResponse scanOutbound(@Valid @RequestBody ScanOutboundRequest request) {
        return outboundPickingService.scanOutbound(request);
    }

    @PostMapping("/recommend")
    public List<PickRecommendation> recommend(@Valid @RequestBody RecommendRequest request) {
        return outboundPickingService.recommendPick(
                request.materialId(), request.warehouseIds(), request.neededQty());
    }
}
