package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.InboundOrder;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.InboundOrderMapper;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
import com.scut.wms.masterdata.StorageLocationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class InventoryService {
    private static final String PRINTED = "PRINTED";
    private static final String RECEIVED = "RECEIVED";
    private static final String RELEASED = "RELEASED";
    private static final String PARTIAL_RECEIVED = "PARTIAL_RECEIVED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String INBOUND_RECEIVE = "INBOUND_RECEIVE";
    private static final String KANBAN_BOARD = "KANBAN_BOARD";
    private static final DateTimeFormatter MOVEMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final KanbanBoardMapper kanbanBoardMapper;
    private final StorageLocationMapper storageLocationMapper;

    public InventoryService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InboundOrderMapper inboundOrderMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            KanbanBoardMapper kanbanBoardMapper,
            StorageLocationMapper storageLocationMapper
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.storageLocationMapper = storageLocationMapper;
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

        // Determine actual location: request override or kanban's default
        Long actualLocationId = (request.locationId() != null) ? request.locationId() : context.getTargetLocationId();

        // If forced inbound (different location), validate warehouse consistency (D27)
        if (!Objects.equals(actualLocationId, context.getTargetLocationId())) {
            var actualLocation = storageLocationMapper.selectById(actualLocationId);
            if (actualLocation == null) {
                throw new BusinessException("目标库位不存在");
            }
            if (!Objects.equals(actualLocation.getWarehouseId(), context.getLocationWarehouseId())) {
                throw new BusinessException("强制入库的库位必须属于同一仓库");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryMovement movement = createMovement(context, now);
        inventoryMovementMapper.insert(movement);

        InventoryBalance balance = upsertBalance(context, actualLocationId);

        KanbanBoard board = requireKanban(context.getKanbanId());
        if (!Objects.equals(board.getLocationId(), actualLocationId)) {
            board.setLocationId(actualLocationId);
        }
        board.setStatus(RECEIVED);
        board.setReceivedAt(now);
        kanbanBoardMapper.updateById(board);

        InboundOrderLine line = requireLine(context.getLineId());
        line.setReceivedQty(line.getReceivedQty().add(context.getBoardQty()));
        inboundOrderLineMapper.updateById(line);

        InboundOrder order = requireLockedOrder(context.getOrderId());
        refreshOrderStatus(order, now);

        // Look up location names for response
        String actualLocationName = context.getLocationName();
        if (!Objects.equals(actualLocationId, context.getTargetLocationId())) {
            var actualLocation = storageLocationMapper.selectById(actualLocationId);
            actualLocationName = (actualLocation != null) ? actualLocation.getLocationName() : null;
        }
        String plannedLocationName = null;
        if (context.getPlannedLocationId() != null) {
            var plannedLocation = storageLocationMapper.selectById(context.getPlannedLocationId());
            plannedLocationName = (plannedLocation != null) ? plannedLocation.getLocationName() : null;
        }

        return new ScanInboundResponse(
                context.getKanbanCode(),
                context.getInboundNo(),
                context.getMaterialCode(),
                context.getMaterialName(),
                context.getBoardQty(),
                actualLocationName,
                order.getStatus(),
                now,
                context.getPlannedLocationId(),
                plannedLocationName,
                actualLocationId,
                actualLocationName
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

    @Transactional
    public Map<String, Object> cancelKanban(Long kanbanId) {
        KanbanBoard kanban = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
        if (kanban == null) throw new BusinessException(HttpStatus.NOT_FOUND, "看板不存在");
        if (!PRINTED.equals(kanban.getStatus())) throw new BusinessException("看板状态不允许取消");

        InboundOrder order = inboundOrderMapper.selectById(kanban.getInboundOrderId());
        if (order == null) throw new BusinessException("关联入库单不存在");
        if (!RELEASED.equals(order.getStatus()) && !PARTIAL_RECEIVED.equals(order.getStatus())) {
            throw new BusinessException("入库单状态不允许取消看板");
        }

        kanban.setStatus(CANCELLED);
        kanbanBoardMapper.updateById(kanban);

        recalcPlannedQtyAndRefreshStatus(kanban.getInboundOrderLineId(), kanban.getInboundOrderId());

        return Map.of("cancelled", true, "kanbanCode", kanban.getKanbanCode());
    }

    @Transactional
    public Map<String, Object> cancelKanbansBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要取消的看板");

        List<KanbanBoard> cancelled = new ArrayList<>();
        Set<Long> affectedLineIds = new HashSet<>();
        Set<Long> affectedOrderIds = new HashSet<>();

        for (Long kanbanId : ids) {
            KanbanBoard kanban = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
            if (kanban == null) throw new BusinessException("看板不存在: " + kanbanId);
            if (!PRINTED.equals(kanban.getStatus())) {
                throw new BusinessException("看板 %s 状态不允许取消".formatted(kanban.getKanbanCode()));
            }
            InboundOrder order = inboundOrderMapper.selectById(kanban.getInboundOrderId());
            if (!RELEASED.equals(order.getStatus()) && !PARTIAL_RECEIVED.equals(order.getStatus())) {
                throw new BusinessException("入库单 %s 状态不允许取消看板".formatted(order.getInboundNo()));
            }
            kanban.setStatus(CANCELLED);
            kanbanBoardMapper.updateById(kanban);
            cancelled.add(kanban);
            affectedLineIds.add(kanban.getInboundOrderLineId());
            affectedOrderIds.add(kanban.getInboundOrderId());
        }

        for (Long lineId : affectedLineIds) {
            Long orderId = cancelled.stream()
                    .filter(k -> k.getInboundOrderLineId().equals(lineId))
                    .findFirst().get().getInboundOrderId();
            recalcPlannedQtyAndRefreshStatus(lineId, orderId);
        }

        return Map.of("cancelledCount", cancelled.size());
    }

    private void recalcPlannedQtyAndRefreshStatus(Long lineId, Long orderId) {
        List<KanbanBoard> lineKanbans = kanbanBoardMapper.selectList(Wrappers.<KanbanBoard>lambdaQuery()
                .eq(KanbanBoard::getInboundOrderLineId, lineId)
                .ne(KanbanBoard::getStatus, CANCELLED));
        BigDecimal newPlannedQty = lineKanbans.stream()
                .map(KanbanBoard::getBoardQty)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        InboundOrderLine line = inboundOrderLineMapper.selectById(lineId);
        if (line != null) {
            line.setPlannedQty(newPlannedQty);
            inboundOrderLineMapper.updateById(line);
        }

        InboundOrder order = inboundOrderMapper.selectById(orderId);
        if (order != null) refreshOrderStatus(order, LocalDateTime.now());
    }

    private InventoryMovement createMovement(ScanKanbanContext context, LocalDateTime now) {
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementNo(generateMovementNo(now));
        movement.setMovementType(INBOUND_RECEIVE);
        movement.setSourceType(KANBAN_BOARD);
        movement.setSourceId(context.getKanbanId());
        movement.setKanbanBoardId(context.getKanbanId());
        movement.setMaterialId(context.getMaterialId());
        movement.setPlannedLocationId(context.getTargetLocationId());
        movement.setWarehouseId(context.getTargetWarehouseId());
        movement.setStorageLocationId(context.getTargetLocationId());
        movement.setQty(context.getBoardQty());
        movement.setOccurredAt(now);
        movement.setOperatorName("web");
        return movement;
    }

    private InventoryBalance upsertBalance(ScanKanbanContext context, Long actualLocationId) {
        InventoryBalance balance = inventoryTransactionMapper.selectBalanceForUpdate(
                context.getMaterialId(),
                context.getTargetWarehouseId(),
                actualLocationId
        );
        if (balance == null) {
            balance = new InventoryBalance();
            balance.setMaterialId(context.getMaterialId());
            balance.setWarehouseId(context.getTargetWarehouseId());
            balance.setStorageLocationId(actualLocationId);
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
        InboundOrderLine line = inboundOrderLineMapper.selectByIdForUpdate(lineId);
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
