package com.scut.wms.outbound;

import com.scut.wms.lock.LockService;
import com.scut.wms.outbound.picking.OutboundRecommendationResponse;
import com.scut.wms.outbound.picking.OutboundRecommendationService;
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
    private final LockService lockService;
    private final OutboundRecommendationService recommendationService;

    public OutboundOrderController(
            OutboundOrderService service,
            LockService lockService,
            OutboundRecommendationService recommendationService
    ) {
        this.service = service;
        this.lockService = lockService;
        this.recommendationService = recommendationService;
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

    @GetMapping("/{id}/recommendations")
    public OutboundRecommendationResponse recommendations(@PathVariable Long id) {
        return recommendationService.recommend(id);
    }

    @PostMapping
    public OutboundOrderResponse create(@Valid @RequestBody OutboundOrderRequest request) {
        return service.create(request);
    }

    @PostMapping("/batch")
    public BatchOutboundOrderResponse batchCreate(@Valid @RequestBody BatchOutboundOrderRequest request) {
        return service.createBatch(request);
    }

    @PutMapping("/{id}")
    public OutboundOrderResponse update(@PathVariable Long id, @Valid @RequestBody OutboundOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/cancel")
    public OutboundOrderResponse cancel(@PathVariable Long id) {
        // Release locks before cancelling
        OutboundOrderResponse order = service.getById(id);
        if (OutboundOrder.LOCKED.equals(order.status())) {
            lockService.releaseOrderLocks(id);
        }
        return service.cancel(id);
    }

    @GetMapping("/{id}/print")
    public OutboundPrintResponse print(@PathVariable Long id) {
        return service.print(id);
    }
}
