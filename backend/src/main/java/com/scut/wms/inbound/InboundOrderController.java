package com.scut.wms.inbound;

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

@RestController
@RequestMapping("/api/inbound-orders")
public class InboundOrderController {
    private final InboundOrderService service;

    public InboundOrderController(InboundOrderService service) {
        this.service = service;
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
    public InboundOrderResponse release(@PathVariable Long id) {
        return service.release(id);
    }

    @PostMapping("/{id}/cancel")
    public InboundOrderResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
