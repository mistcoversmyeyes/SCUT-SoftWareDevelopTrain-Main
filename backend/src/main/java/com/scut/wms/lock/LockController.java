package com.scut.wms.lock;

import com.scut.wms.outbound.OutboundOrderResponse;
import com.scut.wms.outbound.OutboundOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class LockController {
    private final LockService lockService;
    private final OutboundOrderService outboundOrderService;

    public LockController(LockService lockService, OutboundOrderService outboundOrderService) {
        this.lockService = lockService;
        this.outboundOrderService = outboundOrderService;
    }

    /**
     * 释放并加锁（替代原 release）。
     */
    @PostMapping("/api/outbound-orders/{id}/release-and-lock")
    public OutboundOrderResponse releaseAndLock(
            @PathVariable Long id,
            @Valid @RequestBody ReleaseAndLockRequest request
    ) {
        lockService.releaseAndLock(id, request.warehouseIds());
        return outboundOrderService.getById(id);
    }

    /**
     * 锁记录列表（按出库单号/物料/状态筛选）。
     */
    @GetMapping("/api/locks")
    public List<LockOrderSummary> listLocks(
            @RequestParam(required = false) String outboundNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status
    ) {
        return lockService.listLockOrders(outboundNo, materialCode, status);
    }

    /**
     * 锁定明细（按出库单）。
     */
    @GetMapping("/api/locks/{outboundOrderId}/details")
    public List<LockDetailView> listLockDetails(@PathVariable Long outboundOrderId) {
        return lockService.listLockDetails(outboundOrderId);
    }

    /**
     * 解锁。
     */
    @PostMapping("/api/locks/{id}/unlock")
    public Map<String, String> unlock(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String operator = body.getOrDefault("operator", "web");
        lockService.unlock(id, operator);
        return Map.of("message", "解锁成功");
    }

    /**
     * 重新 FIFO 分配。
     */
    @PostMapping("/api/outbound-orders/{id}/reassign")
    public Map<String, String> reassign(@PathVariable Long id) {
        lockService.reassign(id);
        return Map.of("message", "重新分配成功");
    }

    /**
     * 强制出库审计日志。
     */
    @GetMapping("/api/locks/force-logs")
    public List<ForceLogView> forceLogs(@RequestParam(required = false) String outboundNo) {
        return lockService.listForceLogs(outboundNo);
    }

    /**
     * 出库单锁定物料清单（按单号查询，扫出库单二维码后获取）。
     */
    @GetMapping("/api/outbound-orders/no/{outboundNo}/qr-info")
    public QrInfoResponse qrInfoByNo(@PathVariable String outboundNo) {
        OutboundOrderResponse order = outboundOrderService.getByOutboundNo(outboundNo);
        List<LockDetailView> details = lockService.listLockDetails(order.id());
        return new QrInfoResponse(order, details);
    }

    /**
     * 强制出库候选看板列表（用于强制扫码页面展示）。
     */
    @GetMapping("/api/outbound-orders/{id}/force-candidates")
    public ForceCandidateResponse forceCandidates(@PathVariable Long id) {
        return lockService.getForceCandidates(id);
    }

    public record ReleaseAndLockRequest(
            @NotEmpty(message = "请至少选择一个出库仓库")
            List<Long> warehouseIds
    ) {}

    public record QrInfoResponse(OutboundOrderResponse order, List<LockDetailView> lockedItems) {}
}
