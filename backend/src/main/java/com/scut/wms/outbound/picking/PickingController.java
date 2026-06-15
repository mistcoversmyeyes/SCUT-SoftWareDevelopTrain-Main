package com.scut.wms.outbound.picking;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/outbound")
public class PickingController {
    private final OutboundPickingService outboundPickingService;

    public PickingController(OutboundPickingService outboundPickingService) {
        this.outboundPickingService = outboundPickingService;
    }

    @PostMapping("/scan")
    public ScanOutboundResponse scanOutbound(@Valid @RequestBody ScanOutboundRequest request) {
        return outboundPickingService.scanOutbound(request);
    }
}
