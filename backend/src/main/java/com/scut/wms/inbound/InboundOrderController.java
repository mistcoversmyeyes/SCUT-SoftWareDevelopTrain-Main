package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inbound-orders")
public class InboundOrderController {
    private final InboundOrderService service;
    private final InventoryTagMapper inventoryTagMapper;

    public InboundOrderController(InboundOrderService service, InventoryTagMapper inventoryTagMapper) {
        this.service = service;
        this.inventoryTagMapper = inventoryTagMapper;
    }

    @GetMapping
    public List<InboundOrderResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inboundNo,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String supplier
    ) {
        return service.list(status, inboundNo, supplierId, supplier);
    }

    @PostMapping
    public InboundOrderResponse create(@Valid @RequestBody InboundOrderRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public InboundOrderResponse update(@PathVariable Long id, @Valid @RequestBody InboundOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/release")
    public Map<String, Object> release(@PathVariable Long id) {
        InboundOrderResponse resp = service.release(id);
        List<InventoryTag> inventoryTags = inventoryTagMapper.selectList(Wrappers.<InventoryTag>lambdaQuery()
                .eq(InventoryTag::getInboundOrderId, id));
        return Map.of("order", resp, "inventoryTagCount", inventoryTags.size(),
                "inventoryTagCodes", inventoryTags.stream().map(InventoryTag::getInventoryTagCode).toList());
    }

    @PostMapping("/{id}/cancel")
    public InboundOrderResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/print")
    public InboundPrintResponse print(@PathVariable Long id) {
        return service.print(id);
    }

    @GetMapping("/{id}")
    public InboundOrderResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/inventory-tags")
    public List<InventoryTagPrintResponse> getInventoryTags(@PathVariable Long id) {
        return service.printInventoryTags(id);
    }

    @GetMapping("/{id}/inventory-tags/print")
    public List<InventoryTagPrintResponse> printInventoryTags(@PathVariable Long id) {
        return service.printInventoryTags(id);
    }
}
