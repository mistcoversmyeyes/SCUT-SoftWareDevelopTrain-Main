package com.scut.wms.outbound.picking;

import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
import com.scut.wms.inventory.InventoryBalance;
import com.scut.wms.inventory.InventoryBalanceMapper;
import com.scut.wms.inventory.InventoryMovement;
import com.scut.wms.inventory.InventoryMovementMapper;
import com.scut.wms.inventory.InventoryTransactionMapper;
import com.scut.wms.inventory.ScanKanbanContext;
import com.scut.wms.lock.LockService;
import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import com.scut.wms.outbound.OutboundOrderMapper;
import com.scut.wms.outbound.OutboundOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class OutboundPickingService {
    private static final String RECEIVED = "RECEIVED";
    private static final String SHIPPED = "SHIPPED";
    private static final String LOCKED = "LOCKED";
    private static final String OUTBOUND_PICK = "OUTBOUND_PICK";
    private static final String KANBAN_BOARD = "KANBAN_BOARD";
    private static final DateTimeFormatter MOVEMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final KanbanBoardMapper kanbanBoardMapper;
    private final OutboundOrderService outboundOrderService;
    private final OutboundOrderLineMapper outboundOrderLineMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final LockService lockService;

    public OutboundPickingService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            KanbanBoardMapper kanbanBoardMapper,
            OutboundOrderService outboundOrderService,
            OutboundOrderLineMapper outboundOrderLineMapper,
            OutboundOrderMapper outboundOrderMapper,
            LockService lockService
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.outboundOrderService = outboundOrderService;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.lockService = lockService;
    }

    /**
     * 带单出库：扫看板码出库。
     * force=true 时跳过锁校验，抢锁算本单。
     */
    @Transactional
    public ScanOutboundResponse pickWithOrder(ScanOutboundRequest request, boolean force) {
        ScanKanbanContext ctx = inventoryTransactionMapper.selectScanKanbanForUpdate(request.kanbanCode());
        if (ctx == null) {
            throw new BusinessException("未找到看板");
        }

        if (!force) {
            // Normal: kanban must be LOCKED by this order
            if (!LOCKED.equals(ctx.getKanbanStatus())) {
                throw new BusinessException("看板未锁定，当前状态: " + ctx.getKanbanStatus());
            }
            KanbanBoard board = kanbanBoardMapper.selectById(ctx.getKanbanId());
            if (board == null || !request.outboundOrderId().equals(board.getLockedByOrderId())) {
                throw new BusinessException("该看板未锁定给本出库单");
            }
        } else {
            // Force: if locked by another order, steal it
            KanbanBoard board = kanbanBoardMapper.selectById(ctx.getKanbanId());
            if (board != null && LOCKED.equals(board.getStatus())
                    && !request.outboundOrderId().equals(board.getLockedByOrderId())) {
                lockService.stealLockForOrder(request.outboundOrderId(), ctx.getKanbanId());
            }
        }

        return executePick(ctx, request);
    }

    /**
     * 不带单出库：直接扫看板出库，不管锁状态。
     */
    @Transactional
    public ScanOutboundResponse pickNoOrder(ScanOutboundRequest request) {
        ScanKanbanContext ctx = inventoryTransactionMapper.selectScanKanbanForUpdate(request.kanbanCode());
        if (ctx == null) {
            throw new BusinessException("未找到看板");
        }

        // If kanban is locked, mark as FORCE_STOLEN
        if (LOCKED.equals(ctx.getKanbanStatus())) {
            lockService.markForceStolen(ctx.getKanbanId());
        }

        return executePick(ctx, request);
    }

    private ScanOutboundResponse executePick(ScanKanbanContext ctx, ScanOutboundRequest request) {
        LocalDateTime now = LocalDateTime.now();

        // Resolve outbound order line from kanban's lock if not explicitly provided
        Long effectiveOrderLineId = request.outboundOrderLineId();
        if (request.outboundOrderId() != null && effectiveOrderLineId == null) {
            KanbanBoard kb = kanbanBoardMapper.selectById(ctx.getKanbanId());
            if (kb != null && kb.getLockedByOrderLineId() != null
                    && request.outboundOrderId().equals(kb.getLockedByOrderId())) {
                effectiveOrderLineId = kb.getLockedByOrderLineId();
            }
        }

        // Determine pick quantity
        BigDecimal pickQty = request.qty();
        if (pickQty == null || pickQty.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal boardPicked = ctx.getPickedQty() == null ? BigDecimal.ZERO : ctx.getPickedQty();
            BigDecimal boardRemaining = ctx.getBoardQty().subtract(boardPicked);
            pickQty = boardRemaining;
            // Cap at outbound order line needed
            if (request.outboundOrderId() != null && effectiveOrderLineId != null) {
                OutboundOrderLine line = outboundOrderLineMapper.selectById(effectiveOrderLineId);
                if (line != null && line.getPlannedQty() != null) {
                    BigDecimal linePicked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
                    BigDecimal lineNeeded = line.getPlannedQty().subtract(linePicked);
                    if (lineNeeded.compareTo(pickQty) < 0) {
                        pickQty = lineNeeded;
                    }
                }
            }
        }

        // Validate pick qty
        BigDecimal currentBoardPicked = ctx.getPickedQty() == null ? BigDecimal.ZERO : ctx.getPickedQty();
        BigDecimal remaining = ctx.getBoardQty().subtract(currentBoardPicked);
        if (pickQty.compareTo(remaining) > 0) {
            throw new BusinessException("出库数量超过看板剩余数量");
        }

        // Create movement
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementNo(generateMovementNo(now));
        movement.setMovementType(OUTBOUND_PICK);
        movement.setSourceType(KANBAN_BOARD);
        movement.setSourceId(ctx.getKanbanId());
        movement.setKanbanBoardId(ctx.getKanbanId());
        movement.setMaterialId(ctx.getMaterialId());
        movement.setWarehouseId(ctx.getTargetWarehouseId());
        movement.setStorageLocationId(ctx.getTargetLocationId());
        movement.setQty(pickQty);
        movement.setOccurredAt(now);
        movement.setOperatorName("web");
        movement.setOutboundOrderId(request.outboundOrderId());
        movement.setOutboundOrderLineId(request.outboundOrderLineId());
        movement.setForceOutbound(false);
        inventoryMovementMapper.insert(movement);

        // Subtract balance
        InventoryBalance balance = inventoryTransactionMapper.selectBalanceForUpdate(
                ctx.getMaterialId(),
                ctx.getTargetWarehouseId(),
                ctx.getTargetLocationId()
        );
        if (balance == null || balance.getOnHandQty().compareTo(pickQty) < 0) {
            throw new BusinessException("库存不足");
        }
        balance.setOnHandQty(balance.getOnHandQty().subtract(pickQty));
        inventoryBalanceMapper.updateById(balance);

        // Update kanban
        KanbanBoard board = kanbanBoardMapper.selectById(ctx.getKanbanId());
        if (board == null) {
            throw new BusinessException("看板不存在");
        }
        BigDecimal curPicked = board.getPickedQty() == null ? BigDecimal.ZERO : board.getPickedQty();
        board.setPickedQty(curPicked.add(pickQty));
        if (board.getPickedQty().compareTo(board.getBoardQty()) >= 0) {
            board.setStatus(SHIPPED);
            board.setLockedByOrderId(null);
            board.setLockedByOrderLineId(null);
        }
        kanbanBoardMapper.updateById(board);

        // Handle outbound order association
        String orderStatus = null;
        String outboundNo = null;
        if (request.outboundOrderId() != null && effectiveOrderLineId != null) {
            outboundOrderService.addPickedQty(request.outboundOrderId(), effectiveOrderLineId, pickQty);
            orderStatus = outboundOrderService.getOrderStatus(request.outboundOrderId());
        }
        if (request.outboundOrderId() != null) {
            OutboundOrder oo = outboundOrderMapper.selectById(request.outboundOrderId());
            if (oo != null) outboundNo = oo.getOutboundNo();
        }

        return new ScanOutboundResponse(
                ctx.getKanbanCode(),
                ctx.getMaterialCode(),
                ctx.getMaterialName(),
                ctx.getLocationName(),
                pickQty,
                board.getStatus(),
                now,
                request.outboundOrderId(),
                effectiveOrderLineId,
                outboundNo,
                orderStatus
        );
    }

    public ScanKanbanContext lookupKanban(String kanbanCode) {
        ScanKanbanContext ctx = inventoryTransactionMapper.selectKanbanContext(kanbanCode);
        // Also include lock info
        if (ctx != null) {
            KanbanBoard board = kanbanBoardMapper.selectById(ctx.getKanbanId());
            if (board != null && LOCKED.equals(board.getStatus())) {
                // Prefix status with lock info for display
            }
        }
        return ctx;
    }

    private String generateMovementNo(LocalDateTime now) {
        return "MV-%s-%s".formatted(
                now.format(MOVEMENT_NO_TIME),
                UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        );
    }
}
