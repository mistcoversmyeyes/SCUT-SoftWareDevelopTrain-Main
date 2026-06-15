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

    public OutboundPickingService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            KanbanBoardMapper kanbanBoardMapper
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
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
        movement.setQty(ctx.getBoardQty());
        movement.setOccurredAt(now);
        movement.setOperatorName("web");
        inventoryMovementMapper.insert(movement);

        // Upsert balance (subtract)
        InventoryBalance balance = inventoryTransactionMapper.selectBalanceForUpdate(
                ctx.getMaterialId(),
                ctx.getTargetWarehouseId(),
                ctx.getTargetLocationId()
        );
        if (balance == null || balance.getOnHandQty().compareTo(ctx.getBoardQty()) < 0) {
            throw new BusinessException("库存不足");
        }
        balance.setOnHandQty(balance.getOnHandQty().subtract(ctx.getBoardQty()));
        inventoryBalanceMapper.updateById(balance);

        // Update kanban: increment pickedQty, possibly mark SHIPPED
        KanbanBoard board = kanbanBoardMapper.selectById(ctx.getKanbanId());
        if (board == null) {
            throw new BusinessException("看板不存在");
        }
        BigDecimal currentPicked = board.getPickedQty() == null ? BigDecimal.ZERO : board.getPickedQty();
        board.setPickedQty(currentPicked.add(ctx.getBoardQty()));
        if (board.getPickedQty().compareTo(board.getBoardQty()) >= 0) {
            board.setStatus(SHIPPED);
        }
        kanbanBoardMapper.updateById(board);

        return new ScanOutboundResponse(
                ctx.getKanbanCode(),
                ctx.getMaterialCode(),
                ctx.getMaterialName(),
                ctx.getLocationName(),
                ctx.getBoardQty(),
                board.getStatus(),
                now
        );
    }

    private String generateMovementNo(LocalDateTime now) {
        return "MV-%s-%s".formatted(
                now.format(MOVEMENT_NO_TIME),
                UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        );
    }
}
