package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.InboundOrder;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.InboundOrderMapper;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class InventoryService {
    private static final String PRINTED = "PRINTED";
    private static final String RECEIVED = "RECEIVED";
    private static final String RELEASED = "RELEASED";
    private static final String PARTIAL_RECEIVED = "PARTIAL_RECEIVED";
    private static final String COMPLETED = "COMPLETED";
    private static final String INBOUND_RECEIVE = "INBOUND_RECEIVE";
    private static final String KANBAN_BOARD = "KANBAN_BOARD";
    private static final DateTimeFormatter MOVEMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final KanbanBoardMapper kanbanBoardMapper;

    public InventoryService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InboundOrderMapper inboundOrderMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            KanbanBoardMapper kanbanBoardMapper
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
    }

    @Transactional
    public ScanInboundResponse scanInbound(ScanInboundRequest request) {
        ScanKanbanContext context = inventoryTransactionMapper.selectScanKanbanForUpdate(request.kanbanCode());
        if (context == null) {
            throw new BusinessException("未找到看板");
        }
        if (RECEIVED.equals(context.getKanbanStatus())) {
            throw new BusinessException("重复扫码");
        }
        if (!PRINTED.equals(context.getKanbanStatus())) {
            throw new BusinessException("看板状态不允许入库");
        }
        if (!RELEASED.equals(context.getOrderStatus()) && !PARTIAL_RECEIVED.equals(context.getOrderStatus())) {
            throw new BusinessException("单据状态不允许入库");
        }
        if (!Objects.equals(context.getTargetWarehouseId(), context.getLocationWarehouseId())) {
            throw new BusinessException("目标库位不属于目标仓库");
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryMovement movement = createMovement(context, now);
        inventoryMovementMapper.insert(movement);

        InventoryBalance balance = upsertBalance(context);

        KanbanBoard board = requireKanban(context.getKanbanId());
        board.setStatus(RECEIVED);
        board.setReceivedAt(now);
        kanbanBoardMapper.updateById(board);

        InboundOrderLine line = requireLine(context.getLineId());
        line.setReceivedQty(line.getReceivedQty().add(context.getBoardQty()));
        inboundOrderLineMapper.updateById(line);

        InboundOrder order = requireLockedOrder(context.getOrderId());
        refreshOrderStatus(order, now);

        return new ScanInboundResponse(
                context.getKanbanCode(),
                context.getInboundNo(),
                context.getMaterialCode(),
                context.getMaterialName(),
                context.getBoardQty(),
                context.getLocationName(),
                order.getStatus(),
                now
        );
    }

    public List<InventoryBalanceView> listBalances(String materialCode, String warehouseCode, String locationCode) {
        return inventoryTransactionMapper.selectInventoryBalances(materialCode, warehouseCode, locationCode);
    }

    public List<InventoryMovementView> listMovements(
            String materialCode,
            String warehouseCode,
            String locationCode,
            String inboundNo,
            String kanbanCode
    ) {
        return inventoryTransactionMapper.selectInventoryMovements(
                materialCode,
                warehouseCode,
                locationCode,
                inboundNo,
                kanbanCode
        );
    }

    public KanbanTraceView getKanbanTrace(String kanbanCode) {
        KanbanTraceView trace = inventoryTransactionMapper.selectKanbanTrace(kanbanCode);
        if (trace == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "未找到看板");
        }
        return trace;
    }

    private InventoryMovement createMovement(ScanKanbanContext context, LocalDateTime now) {
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementNo(generateMovementNo(now));
        movement.setMovementType(INBOUND_RECEIVE);
        movement.setSourceType(KANBAN_BOARD);
        movement.setSourceId(context.getKanbanId());
        movement.setKanbanBoardId(context.getKanbanId());
        movement.setMaterialId(context.getMaterialId());
        movement.setWarehouseId(context.getTargetWarehouseId());
        movement.setStorageLocationId(context.getTargetLocationId());
        movement.setQty(context.getBoardQty());
        movement.setOccurredAt(now);
        movement.setOperatorName("web");
        return movement;
    }

    private InventoryBalance upsertBalance(ScanKanbanContext context) {
        InventoryBalance balance = inventoryTransactionMapper.selectBalanceForUpdate(
                context.getMaterialId(),
                context.getTargetWarehouseId(),
                context.getTargetLocationId()
        );
        if (balance == null) {
            balance = new InventoryBalance();
            balance.setMaterialId(context.getMaterialId());
            balance.setWarehouseId(context.getTargetWarehouseId());
            balance.setStorageLocationId(context.getTargetLocationId());
            balance.setOnHandQty(context.getBoardQty());
            inventoryBalanceMapper.insert(balance);
            return balance;
        }

        balance.setOnHandQty(balance.getOnHandQty().add(context.getBoardQty()));
        inventoryBalanceMapper.updateById(balance);
        return balance;
    }

    private InboundOrder requireLockedOrder(Long orderId) {
        InboundOrder order = inboundOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入库单不存在");
        }
        return order;
    }

    private InboundOrderLine requireLine(Long lineId) {
        InboundOrderLine line = inboundOrderLineMapper.selectById(lineId);
        if (line == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入库单明细不存在");
        }
        return line;
    }

    private KanbanBoard requireKanban(Long kanbanId) {
        KanbanBoard board = kanbanBoardMapper.selectById(kanbanId);
        if (board == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "看板不存在");
        }
        return board;
    }

    private void refreshOrderStatus(InboundOrder order, LocalDateTime now) {
        List<InboundOrderLine> lines = inboundOrderLineMapper.selectList(Wrappers.<InboundOrderLine>lambdaQuery()
                .eq(InboundOrderLine::getInboundOrderId, order.getId()));
        boolean completed = lines.stream()
                .allMatch(line -> line.getReceivedQty().compareTo(line.getPlannedQty()) >= 0);
        inboundOrderMapper.update(null, Wrappers.<InboundOrder>lambdaUpdate()
                .eq(InboundOrder::getId, order.getId())
                .set(InboundOrder::getStatus, completed ? COMPLETED : PARTIAL_RECEIVED)
                .set(InboundOrder::getCompletedAt, completed ? now : null));
        order.setStatus(completed ? COMPLETED : PARTIAL_RECEIVED);
        order.setCompletedAt(completed ? now : null);
    }

    private String generateMovementNo(LocalDateTime now) {
        return "MV-%s-%s".formatted(
                now.format(MOVEMENT_NO_TIME),
                UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        );
    }
}
