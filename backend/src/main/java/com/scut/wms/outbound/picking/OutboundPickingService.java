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
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import com.scut.wms.outbound.OutboundOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OutboundPickingService {
    private static final String RECEIVED = "RECEIVED";
    private static final String SHIPPED = "SHIPPED";
    private static final String OUTBOUND_PICK = "OUTBOUND_PICK";
    private static final String KANBAN_BOARD = "KANBAN_BOARD";
    private static final DateTimeFormatter MOVEMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final KanbanBoardMapper kanbanBoardMapper;
    private final OutboundOrderService outboundOrderService;
    private final OutboundOrderLineMapper outboundOrderLineMapper;

    public OutboundPickingService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            KanbanBoardMapper kanbanBoardMapper,
            OutboundOrderService outboundOrderService,
            OutboundOrderLineMapper outboundOrderLineMapper
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.outboundOrderService = outboundOrderService;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
    }

    @Transactional
    public ScanOutboundResponse scanOutbound(ScanOutboundRequest request) {
        ScanKanbanContext ctx = inventoryTransactionMapper.selectScanKanbanForUpdate(request.kanbanCode());
        if (ctx == null) {
            throw new BusinessException("未找到看板");
        }
        if (!RECEIVED.equals(ctx.getKanbanStatus())) {
            throw new BusinessException("看板状态不允许出库，当前状态: " + ctx.getKanbanStatus());
        }

        // FIFO check: ensure no earlier received kanbans exist for the same material/warehouse/location
        List<FifoPickCandidate> earlier = inventoryTransactionMapper.selectFifoCandidateForUpdate(
                ctx.getMaterialId(),
                ctx.getTargetWarehouseId(),
                ctx.getTargetLocationId()
        );
        for (FifoPickCandidate candidate : earlier) {
            if (!candidate.getKanbanId().equals(ctx.getKanbanId())
                    && candidate.getReceivedAt() != null
                    && ctx.getReceivedAt() != null
                    && candidate.getReceivedAt().isBefore(ctx.getReceivedAt())) {
                throw new BusinessException("请优先出库更早批次: " + candidate.getKanbanCode());
            }
        }
        LocalDateTime now = LocalDateTime.now();

        // Determine pick quantity
        BigDecimal pickQty = request.qty();
        if (pickQty == null || pickQty.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal boardRemaining = ctx.getBoardQty().subtract(
                    ctx.getPickedQty() == null ? BigDecimal.ZERO : ctx.getPickedQty());
            pickQty = boardRemaining;
            // 带单出库时，全量 = min(看板剩余, 出库单行仍需)
            if (request.outboundOrderId() != null && request.outboundOrderLineId() != null) {
                OutboundOrderLine line = outboundOrderLineMapper.selectById(request.outboundOrderLineId());
                if (line != null && line.getPlannedQty() != null) {
                    BigDecimal linePicked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
                    BigDecimal lineNeeded = line.getPlannedQty().subtract(linePicked);
                    if (lineNeeded.compareTo(pickQty) < 0) {
                        pickQty = lineNeeded;
                    }
                }
            }
        }

        // Validate pick qty does not exceed remaining board qty
        BigDecimal currentPicked = ctx.getPickedQty() == null ? BigDecimal.ZERO : ctx.getPickedQty();
        BigDecimal remaining = ctx.getBoardQty().subtract(currentPicked);
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
        inventoryMovementMapper.insert(movement);

        // Upsert balance (subtract)
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

        // Update kanban: increment pickedQty, possibly mark SHIPPED
        KanbanBoard board = kanbanBoardMapper.selectById(ctx.getKanbanId());
        if (board == null) {
            throw new BusinessException("看板不存在");
        }
        BigDecimal currentPickedBoard = board.getPickedQty() == null ? BigDecimal.ZERO : board.getPickedQty();
        board.setPickedQty(currentPickedBoard.add(pickQty));
        if (board.getPickedQty().compareTo(board.getBoardQty()) >= 0) {
            board.setStatus(SHIPPED);
        }
        kanbanBoardMapper.updateById(board);

        // Handle outbound order association
        String orderStatus = null;
        if (request.outboundOrderId() != null && request.outboundOrderLineId() != null) {
            outboundOrderService.addPickedQty(request.outboundOrderId(), request.outboundOrderLineId(), pickQty);
            orderStatus = outboundOrderService.getOrderStatus(request.outboundOrderId());
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
                request.outboundOrderLineId(),
                orderStatus
        );
    }

    public ScanKanbanContext lookupKanban(String kanbanCode) {
        return inventoryTransactionMapper.selectKanbanContext(kanbanCode);
    }

    public List<PickRecommendation> recommendPick(Long materialId, List<Long> warehouseIds, BigDecimal neededQty) {
        return inventoryTransactionMapper.selectFifoRecommendations(materialId, warehouseIds);
    }

    private String generateMovementNo(LocalDateTime now) {
        return "MV-%s-%s".formatted(
                now.format(MOVEMENT_NO_TIME),
                UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        );
    }
}
