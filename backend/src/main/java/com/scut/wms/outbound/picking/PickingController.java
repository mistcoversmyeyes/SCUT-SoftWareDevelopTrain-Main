package com.scut.wms.outbound.picking;

import com.scut.wms.inventory.ScanKanbanContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/pick-with-order")
    public ScanOutboundResponse pickWithOrder(@Valid @RequestBody ScanOutboundRequest request) {
        return outboundPickingService.pickWithOrder(request, false);
    }

    @PostMapping("/pick-with-order/force")
    public ScanOutboundResponse pickWithOrderForce(@Valid @RequestBody ScanOutboundRequest request) {
        return outboundPickingService.pickWithOrder(request, true);
    }

    @PostMapping("/pick-no-order")
    public ScanOutboundResponse pickNoOrder(@Valid @RequestBody ScanOutboundRequest request) {
        return outboundPickingService.pickNoOrder(request);
    }
}
