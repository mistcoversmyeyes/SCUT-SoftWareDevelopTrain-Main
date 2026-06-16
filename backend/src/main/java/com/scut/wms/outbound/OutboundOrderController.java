package com.scut.wms.outbound;

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
@RequestMapping("/api/outbound-orders")
public class OutboundOrderController {
    private final OutboundOrderService service;

    public OutboundOrderController(OutboundOrderService service) {
        this.service = service;
    }

    @GetMapping
    public List<OutboundOrderResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String outboundNo,
            @RequestParam(required = false) Long supplierId
    ) {
        return service.list(status, outboundNo, supplierId);
    }

    @GetMapping("/{id}")
    public OutboundOrderResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public OutboundOrderResponse create(@Valid @RequestBody OutboundOrderRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public OutboundOrderResponse update(@PathVariable Long id, @Valid @RequestBody OutboundOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/release")
    public OutboundOrderResponse release(@PathVariable Long id) {
        return service.release(id);
    }

    @PostMapping("/{id}/cancel")
    public OutboundOrderResponse cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/start-picking")
    public OutboundOrderResponse startPicking(@PathVariable Long id) {
        return service.startPicking(id);
    }

    @PostMapping("/{id}/suspend")
    public OutboundOrderResponse suspendPicking(@PathVariable Long id) {
        return service.suspendPicking(id);
    }

    @GetMapping("/{id}/print")
    public OutboundPrintResponse print(@PathVariable Long id) {
        return service.print(id);
    }
}
