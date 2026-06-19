package com.scut.wms.inventory;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/scan-inbound")
    public ScanInboundResponse scanInbound(@Valid @RequestBody ScanInboundRequest request) {
        return inventoryService.scanInbound(request);
    }

    @PostMapping("/kanbans/{kanbanId}/cancel")
    public Map<String, Object> cancelKanban(@PathVariable Long kanbanId) {
        return inventoryService.cancelKanban(kanbanId);
    }

    @PostMapping("/kanbans/cancel")
    public Map<String, Object> cancelKanbansBatch(@RequestBody Map<String, List<Long>> body) {
        return inventoryService.cancelKanbansBatch(body.get("ids"));
    }
}
