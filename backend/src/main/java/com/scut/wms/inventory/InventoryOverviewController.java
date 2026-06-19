package com.scut.wms.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryOverviewController {

    private final InventoryOverviewService overviewService;

    public InventoryOverviewController(InventoryOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/overview")
    public InventoryOverviewResponse overview() {
        return overviewService.overview();
    }
}
