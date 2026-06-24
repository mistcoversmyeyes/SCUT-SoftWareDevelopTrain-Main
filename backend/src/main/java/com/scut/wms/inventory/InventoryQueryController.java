package com.scut.wms.inventory;

import com.scut.wms.inbound.InboundOrderResponse;
import com.scut.wms.inbound.InboundOrderService;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
import com.scut.wms.inbound.InventoryTagPrintResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {
    private final InventoryService inventoryService;
    private final InboundOrderService inboundOrderService;
    private final InventoryTagMapper inventoryTagMapper;

    public InventoryQueryController(InventoryService inventoryService, InboundOrderService inboundOrderService, InventoryTagMapper inventoryTagMapper) {
        this.inventoryService = inventoryService;
        this.inboundOrderService = inboundOrderService;
        this.inventoryTagMapper = inventoryTagMapper;
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
            @RequestParam(required = false) String inventoryTagCode
    ) {
        return inventoryService.listMovements(materialCode, warehouseCode, locationCode, inboundNo, inventoryTagCode);
    }

    @GetMapping("/inventory-tag-lookup")
    public ScanInventoryTagContext lookupInventoryTag(@RequestParam String inventoryTagCode) {
        return inventoryService.lookupInventoryTag(inventoryTagCode);
    }

    @GetMapping("/inbound-orders/{id}")
    public InboundOrderResponse getInboundOrder(@PathVariable Long id) {
        return inboundOrderService.getById(id);
    }

    @GetMapping("/inventory-tags")
    public List<InventoryTagPrintResponse> getInventoryTags(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inboundNo,
            @RequestParam(required = false) String materialCode
    ) {
        return inboundOrderService.listInventoryTagPrints(status, inboundNo, materialCode);
    }

    @GetMapping("/inbound-orders/{id}/inventory-tags")
    public List<InventoryTagPrintResponse> getOrderInventoryTags(@PathVariable Long id) {
        return inboundOrderService.printInventoryTags(id);
    }

}
