package com.scut.wms.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {
    private final InventoryService inventoryService;

    public InventoryQueryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/balances")
    public List<InventoryBalanceView> balances(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String locationCode
    ) {
        return inventoryService.listBalances(materialCode, warehouseCode, locationCode);
    }

    @GetMapping("/movements")
    public List<InventoryMovementView> movements(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam(required = false) String locationCode,
            @RequestParam(required = false) String inboundNo,
            @RequestParam(required = false) String kanbanCode
    ) {
        return inventoryService.listMovements(materialCode, warehouseCode, locationCode, inboundNo, kanbanCode);
    }
}
