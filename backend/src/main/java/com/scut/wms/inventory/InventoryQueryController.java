package com.scut.wms.inventory;

import com.scut.wms.inbound.InboundOrderResponse;
import com.scut.wms.inbound.InboundOrderService;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
import com.scut.wms.inbound.KanbanPrintResponse;
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
    private final KanbanBoardMapper kanbanBoardMapper;

    public InventoryQueryController(InventoryService inventoryService, InboundOrderService inboundOrderService, KanbanBoardMapper kanbanBoardMapper) {
        this.inventoryService = inventoryService;
        this.inboundOrderService = inboundOrderService;
        this.kanbanBoardMapper = kanbanBoardMapper;
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

    @GetMapping("/inbound-orders/{id}")
    public InboundOrderResponse getInboundOrder(@PathVariable Long id) {
        return inboundOrderService.getById(id);
    }

    @GetMapping("/kanbans")
    public List<KanbanPrintResponse> getKanbans(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inboundNo,
            @RequestParam(required = false) String materialCode
    ) {
        return inboundOrderService.listKanbanPrints(status, inboundNo, materialCode);
    }

    @GetMapping("/inbound-orders/{id}/kanbans")
    public List<KanbanPrintResponse> getOrderKanbans(@PathVariable Long id) {
        return inboundOrderService.printKanbans(id);
    }

}