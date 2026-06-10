package com.scut.wms.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kanbans")
public class KanbanTraceController {
    private final InventoryService inventoryService;

    public KanbanTraceController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{kanbanCode}/trace")
    public KanbanTraceView trace(@PathVariable String kanbanCode) {
        return inventoryService.getKanbanTrace(kanbanCode);
    }
}
