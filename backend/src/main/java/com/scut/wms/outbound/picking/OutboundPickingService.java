package com.scut.wms.outbound.picking;

import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
import com.scut.wms.inventory.InventoryBalance;
import com.scut.wms.inventory.InventoryBalanceMapper;
import com.scut.wms.inventory.InventoryMovement;
import com.scut.wms.inventory.InventoryMovementMapper;
import com.scut.wms.inventory.InventoryTransactionMapper;
import com.scut.wms.inventory.ScanInventoryTagContext;
import com.scut.wms.lock.InventoryHoldService;
import com.scut.wms.lock.LockService;
import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import com.scut.wms.outbound.OutboundOrderMapper;
import com.scut.wms.outbound.OutboundOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class OutboundPickingService {
    private static final String SHIPPED = "SHIPPED";
    private static final String LOCKED = "LOCKED";
    private static final String OUTBOUND_PICK = "OUTBOUND_PICK";
    private static final String INVENTORY_TAG = "INVENTORY_TAG";
    private static final DateTimeFormatter MOVEMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTagMapper inventoryTagMapper;
    private final OutboundOrderService outboundOrderService;
    private final OutboundOrderLineMapper outboundOrderLineMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final LockService lockService;
    private final InventoryHoldService inventoryHoldService;
    private final OutboundRecommendationService recommendationService;

    public OutboundPickingService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTagMapper inventoryTagMapper,
            OutboundOrderService outboundOrderService,
            OutboundOrderLineMapper outboundOrderLineMapper,
            OutboundOrderMapper outboundOrderMapper,
            LockService lockService,
            InventoryHoldService inventoryHoldService,
            OutboundRecommendationService recommendationService
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTagMapper = inventoryTagMapper;
        this.outboundOrderService = outboundOrderService;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.lockService = lockService;
        this.inventoryHoldService = inventoryHoldService;
        this.recommendationService = recommendationService;
    }

    @Transactional
    public ScanOutboundResponse pickWithOrder(ScanOutboundRequest request, boolean force) {
        ScanInventoryTagContext ctx = inventoryTransactionMapper.selectScanInventoryTagForUpdate(request.inventoryTagCode());
        if (ctx == null) {
            throw new BusinessException("未找到库存标签");
        }
        InventoryTag board = requireBoard(ctx.getInventoryTagId());
        inventoryHoldService.ensureOrderOutboundAllowed(board);

        if (!force) {
            if (request.outboundOrderId() == null || request.outboundOrderLineId() == null) {
                throw new BusinessException("带单出库必须指定出库单和明细行");
            }
            OutboundOrder order = outboundOrderMapper.selectById(request.outboundOrderId());
            if (order == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "出库单不存在");
            }
            OutboundOrderLine line = outboundOrderLineMapper.selectById(request.outboundOrderLineId());
            if (line == null || !line.getOutboundOrderId().equals(request.outboundOrderId())) {
                throw new BusinessException("出库单明细行不存在");
            }
            if (!line.getMaterialId().equals(ctx.getMaterialId())) {
                throw new BusinessException("库存标签物料与出库明细不一致");
            }
            if (LOCKED.equals(ctx.getInventoryTagStatus()) && !request.outboundOrderId().equals(board.getLockedByOrderId())) {
                throw new BusinessException("该库存标签未锁定给本出库单");
            }
            boolean lockedForThisOrder = LOCKED.equals(ctx.getInventoryTagStatus())
                    && request.outboundOrderId().equals(board.getLockedByOrderId());
            boolean recommended = lockedForThisOrder || recommendationService.containsRecommendedTag(
                    request.outboundOrderLineId(),
                    request.inventoryTagCode()
            );
            if (!recommended && !request.isConfirmNonRecommended()) {
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "当前出库库存标签不在推荐出库方案中，是否继续按非推荐方案出库？"
                );
            }
            if (OutboundOrder.DRAFT.equals(order.getStatus())) {
                order.setStatus(OutboundOrder.PICKING);
                outboundOrderMapper.updateById(order);
            }
        } else if (LOCKED.equals(board.getStatus()) && board.getLockedByOrderId() != null
                && !request.outboundOrderId().equals(board.getLockedByOrderId())) {
            lockService.stealLockForOrder(request.outboundOrderId(), ctx.getInventoryTagId());
            board = requireBoard(ctx.getInventoryTagId());
            ctx = inventoryTransactionMapper.selectScanInventoryTagForUpdate(request.inventoryTagCode());
        }

        return executePick(ctx, board, request, force);
    }

    @Transactional
    public ScanOutboundResponse pickNoOrder(ScanOutboundRequest request) {
        ScanInventoryTagContext ctx = inventoryTransactionMapper.selectScanInventoryTagForUpdate(request.inventoryTagCode());
        if (ctx == null) {
            throw new BusinessException("未找到库存标签");
        }
        InventoryTag board = requireBoard(ctx.getInventoryTagId());
        inventoryHoldService.ensureNormalOutboundAllowed(board);

        if (LOCKED.equals(ctx.getInventoryTagStatus())) {
            lockService.markForceStolen(ctx.getInventoryTagId());
        }
        lockService.createForceAudit(null, ctx);
        return executePick(ctx, board, request, true);
    }

    private ScanOutboundResponse executePick(ScanInventoryTagContext ctx, InventoryTag board, ScanOutboundRequest request, boolean forceOutbound) {
        LocalDateTime now = LocalDateTime.now();

        Long effectiveOrderLineId = request.outboundOrderLineId();
        if (request.outboundOrderId() != null && effectiveOrderLineId == null
                && request.outboundOrderId().equals(board.getLockedByOrderId())) {
            effectiveOrderLineId = board.getLockedByOrderLineId();
        }

        if (!forceOutbound) {
            inventoryHoldService.assertNormalFifoPick(request.outboundOrderId(), effectiveOrderLineId, ctx.getInventoryTagId());
        }

        BigDecimal pickQty = request.qty();
        if (pickQty == null || pickQty.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal boardPicked = ctx.getPickedQty() == null ? BigDecimal.ZERO : ctx.getPickedQty();
            BigDecimal boardRemaining = ctx.getBoardQty().subtract(boardPicked);
            pickQty = boardRemaining;
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

        BigDecimal currentBoardPicked = ctx.getPickedQty() == null ? BigDecimal.ZERO : ctx.getPickedQty();
        BigDecimal remaining = ctx.getBoardQty().subtract(currentBoardPicked);
        if (pickQty.compareTo(remaining) > 0) {
            throw new BusinessException("出库数量超过库存标签剩余数量");
        }

        InventoryMovement movement = new InventoryMovement();
        movement.setMovementNo(generateMovementNo(now));
        movement.setMovementType(OUTBOUND_PICK);
        movement.setSourceType(INVENTORY_TAG);
        movement.setSourceId(ctx.getInventoryTagId());
        movement.setInventoryTagId(ctx.getInventoryTagId());
        movement.setMaterialId(ctx.getMaterialId());
        movement.setWarehouseId(ctx.getTargetWarehouseId());
        movement.setStorageLocationId(ctx.getTargetLocationId());
        movement.setQty(pickQty);
        movement.setOccurredAt(now);
        movement.setOperatorName("web");
        movement.setOutboundOrderId(request.outboundOrderId());
        movement.setOutboundOrderLineId(effectiveOrderLineId);
        movement.setForceOutbound(forceOutbound);
        inventoryMovementMapper.insert(movement);

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

        BigDecimal curPicked = board.getPickedQty() == null ? BigDecimal.ZERO : board.getPickedQty();
        board.setPickedQty(curPicked.add(pickQty));
        if (board.getPickedQty().compareTo(board.getBoardQty()) >= 0) {
            board.setStatus(SHIPPED);
            board.setLockedByOrderId(null);
            board.setLockedByOrderLineId(null);
        }
        inventoryTagMapper.updateById(board);

        String orderStatus = null;
        String outboundNo = null;
        if (request.outboundOrderId() != null && effectiveOrderLineId != null) {
            outboundOrderService.addPickedQty(request.outboundOrderId(), effectiveOrderLineId, pickQty);
            orderStatus = outboundOrderService.getOrderStatus(request.outboundOrderId());
        }
        if (request.outboundOrderId() != null) {
            OutboundOrder oo = outboundOrderMapper.selectById(request.outboundOrderId());
            if (oo != null) {
                outboundNo = oo.getOutboundNo();
            }
        }

        return new ScanOutboundResponse(
                ctx.getInventoryTagCode(),
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

    public ScanInventoryTagContext lookupInventoryTag(String inventoryTagCode) {
        return inventoryTransactionMapper.selectInventoryTagContext(inventoryTagCode);
    }

    private InventoryTag requireBoard(Long inventoryTagId) {
        InventoryTag board = inventoryTagMapper.selectById(inventoryTagId);
        if (board == null) {
            throw new BusinessException("库存标签不存在");
        }
        return board;
    }

    private String generateMovementNo(LocalDateTime now) {
        return "MV-%s-%s".formatted(
                now.format(MOVEMENT_NO_TIME),
                UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        );
    }
}
