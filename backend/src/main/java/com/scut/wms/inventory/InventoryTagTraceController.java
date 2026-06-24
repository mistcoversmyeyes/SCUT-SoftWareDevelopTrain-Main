package com.scut.wms.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory-tags")
public class InventoryTagTraceController {
    private final InventoryService inventoryService;

    public InventoryTagTraceController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{inventoryTagCode}/trace")
    public InventoryTagTraceView trace(@PathVariable String inventoryTagCode) {
        return inventoryService.getInventoryTagTrace(inventoryTagCode);
    }
}
