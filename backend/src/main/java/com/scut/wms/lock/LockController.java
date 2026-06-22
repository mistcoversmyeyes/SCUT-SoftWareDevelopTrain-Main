package com.scut.wms.lock;

import com.scut.wms.outbound.OutboundOrderResponse;
import com.scut.wms.outbound.OutboundOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
public class LockController {
    private final LockService lockService;
    private final OutboundOrderService outboundOrderService;
    private final InventoryHoldService inventoryHoldService;

    public LockController(
            LockService lockService,
            OutboundOrderService outboundOrderService,
            InventoryHoldService inventoryHoldService
    ) {
        this.lockService = lockService;
        this.outboundOrderService = outboundOrderService;
        this.inventoryHoldService = inventoryHoldService;
    }

    @PostMapping("/api/outbound-orders/{id}/release-and-lock")
    public OutboundOrderResponse releaseAndLock(
            @PathVariable Long id,
            @Valid @RequestBody ReleaseAndLockRequest request
    ) {
        lockService.releaseAndLock(id, request.warehouseIds());
        return outboundOrderService.getById(id);
    }

    @GetMapping("/api/locks")
    public List<LockOrderSummary> listLocks(
            @RequestParam(required = false) String outboundNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status
    ) {
        return lockService.listLockOrders(outboundNo, materialCode, status);
    }

    @GetMapping("/api/locks/{outboundOrderId}/details")
    public List<LockDetailView> listLockDetails(@PathVariable Long outboundOrderId) {
        return lockService.listLockDetails(outboundOrderId);
    }

    @PostMapping("/api/locks/{id}/unlock")
    public Map<String, String> unlock(
            @PathVariable Long id,
            @RequestBody Map<String, String> body
    ) {
        String operator = body.getOrDefault("operator", "web");
        lockService.unlock(id, operator);
        return Map.of("message", "解锁成功");
    }

    @PostMapping("/api/outbound-orders/{id}/reassign")
    public Map<String, String> reassign(@PathVariable Long id) {
        lockService.reassign(id);
        return Map.of("message", "重新分配成功");
    }

    @GetMapping("/api/locks/kanbans")
    public List<KanbanLockView> kanbanLocks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String outboundNo
    ) {
        return lockService.listKanbanLocks(status, materialCode, outboundNo);
    }

    @GetMapping("/api/locks/force-logs")
    public List<ForceLogView> forceLogs(@RequestParam(required = false) String outboundNo) {
        return lockService.listForceLogs(outboundNo);
    }

    @GetMapping("/api/holds")
    public List<InventoryHoldView> holds(
            @RequestParam(required = false) String holdType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String kanbanCode
    ) {
        return inventoryHoldService.listHolds(holdType, status, materialCode, kanbanCode);
    }

    @PostMapping("/api/kanbans/{kanbanId}/seal")
    public HoldActionResponse seal(@PathVariable Long kanbanId, @Valid @RequestBody HoldRequest request) {
        return toResponse(inventoryHoldService.seal(kanbanId, request.toCommand()));
    }

    @PostMapping("/api/kanbans/{kanbanId}/unseal")
    public HoldActionResponse unseal(@PathVariable Long kanbanId, @Valid @RequestBody HoldRequest request) {
        return toResponse(inventoryHoldService.unseal(kanbanId, request.toCommand()));
    }

    @PostMapping("/api/kanbans/{kanbanId}/manual-lock")
    public HoldActionResponse manualLock(@PathVariable Long kanbanId, @Valid @RequestBody HoldRequest request) {
        return toResponse(inventoryHoldService.manualLock(kanbanId, request.toCommand()));
    }

    @PostMapping("/api/kanbans/{kanbanId}/manual-unlock")
    public HoldActionResponse manualUnlock(@PathVariable Long kanbanId, @Valid @RequestBody HoldRequest request) {
        return toResponse(inventoryHoldService.manualUnlock(kanbanId, request.toCommand()));
    }

    @GetMapping("/api/outbound-orders/no/{outboundNo}/qr-info")
    public QrInfoResponse qrInfoByNo(@PathVariable String outboundNo) {
        OutboundOrderResponse order = outboundOrderService.getByOutboundNo(outboundNo);
        List<LockDetailView> details = lockService.listLockDetails(order.id());
        return new QrInfoResponse(order, details);
    }

    @GetMapping("/api/outbound-orders/{id}/force-candidates")
    public ForceCandidateResponse forceCandidates(@PathVariable Long id) {
        return lockService.getForceCandidates(id);
    }

    private HoldActionResponse toResponse(InventoryHoldView view) {
        return new HoldActionResponse(
                view.holdId(),
                view.kanbanBoardId(),
                view.holdType(),
                view.status(),
                view.kanbanStatus(),
                view.reason(),
                view.remark(),
                view.operatorName(),
                view.createdAt(),
                view.releasedAt()
        );
    }

    public record ReleaseAndLockRequest(
            @NotEmpty(message = "请至少选择一个出库仓库")
            List<Long> warehouseIds
    ) {}

    public record HoldRequest(
            @NotBlank(message = "原因不能为空") String reason,
            String remark,
            String operator
    ) {
        InventoryHoldService.HoldCommand toCommand() {
            return new InventoryHoldService.HoldCommand(reason, remark, operator);
        }
    }

    public record HoldActionResponse(
            Long holdId,
            Long kanbanBoardId,
            String holdType,
            String holdStatus,
            String status,
            String reason,
            String remark,
            String operatorName,
            LocalDateTime createdAt,
            LocalDateTime releasedAt
    ) {}

    public record QrInfoResponse(OutboundOrderResponse order, List<LockDetailView> lockedItems) {}
}
