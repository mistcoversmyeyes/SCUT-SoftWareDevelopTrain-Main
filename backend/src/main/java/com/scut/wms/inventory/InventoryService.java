package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.InboundOrder;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.InboundOrderMapper;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
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
    private static final String INVENTORY_TAG = "INVENTORY_TAG";
    private static final DateTimeFormatter MOVEMENT_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final InventoryTagMapper inventoryTagMapper;
    private final StorageLocationMapper storageLocationMapper;

    public InventoryService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryMovementMapper inventoryMovementMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InboundOrderMapper inboundOrderMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            InventoryTagMapper inventoryTagMapper,
            StorageLocationMapper storageLocationMapper
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryMovementMapper = inventoryMovementMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.inventoryTagMapper = inventoryTagMapper;
        this.storageLocationMapper = storageLocationMapper;
    }

    @Transactional
    public ScanInboundResponse scanInbound(ScanInboundRequest request) {
        ScanInventoryTagContext context = inventoryTransactionMapper.selectScanInventoryTagForUpdate(request.inventoryTagCode());
        if (context == null) {
            throw new BusinessException("未找到库存标签");
        }
        if (RECEIVED.equals(context.getInventoryTagStatus())) {
            throw new BusinessException("重复扫码");
        }
        if (!PRINTED.equals(context.getInventoryTagStatus())) {
            throw new BusinessException("库存标签状态不允许入库");
        }
        if (!RELEASED.equals(context.getOrderStatus()) && !PARTIAL_RECEIVED.equals(context.getOrderStatus())) {
            throw new BusinessException("单据状态不允许入库");
        }

        // Determine actual location: request override or inventoryTag's default
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

        InventoryTag board = requireInventoryTag(context.getInventoryTagId());
        if (!Objects.equals(board.getLocationId(), actualLocationId)) {
            board.setLocationId(actualLocationId);
        }
        board.setStatus(RECEIVED);
        board.setReceivedAt(now);
        inventoryTagMapper.updateById(board);

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
                context.getInventoryTagCode(),
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
            String inventoryTagCode
    ) {
        return inventoryTransactionMapper.selectInventoryMovements(
                materialCode,
                warehouseCode,
                locationCode,
                inboundNo,
                inventoryTagCode
        );
    }

    public ScanInventoryTagContext lookupInventoryTag(String inventoryTagCode) {
        ScanInventoryTagContext context = inventoryTransactionMapper.selectInventoryTagContext(inventoryTagCode);
        if (context == null) {
            throw new BusinessException("未找到库存标签");
        }
        return context;
    }

    public InventoryTagTraceView getInventoryTagTrace(String inventoryTagCode) {
        InventoryTagTraceView trace = inventoryTransactionMapper.selectInventoryTagTrace(inventoryTagCode);
        if (trace == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "未找到库存标签");
        }
        return trace;
    }

    @Transactional
    public Map<String, Object> cancelInventoryTag(Long inventoryTagId) {
        InventoryTag inventoryTag = inventoryTagMapper.selectByIdForUpdate(inventoryTagId);
        if (inventoryTag == null) throw new BusinessException(HttpStatus.NOT_FOUND, "库存标签不存在");
        if (!PRINTED.equals(inventoryTag.getStatus())) throw new BusinessException("库存标签状态不允许取消");

        InboundOrder order = inboundOrderMapper.selectById(inventoryTag.getInboundOrderId());
        if (order == null) throw new BusinessException("关联入库单不存在");
        if (!RELEASED.equals(order.getStatus()) && !PARTIAL_RECEIVED.equals(order.getStatus())) {
            throw new BusinessException("入库单状态不允许取消库存标签");
        }

        inventoryTag.setStatus(CANCELLED);
        inventoryTagMapper.updateById(inventoryTag);

        recalcPlannedQtyAndRefreshStatus(inventoryTag.getInboundOrderLineId(), inventoryTag.getInboundOrderId());

        return Map.of("cancelled", true, "inventoryTagCode", inventoryTag.getInventoryTagCode());
    }

    @Transactional
    public Map<String, Object> cancelInventoryTagsBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new BusinessException("请选择要取消的库存标签");

        List<InventoryTag> cancelled = new ArrayList<>();
        Set<Long> affectedLineIds = new HashSet<>();
        Set<Long> affectedOrderIds = new HashSet<>();

        for (Long inventoryTagId : ids) {
            InventoryTag inventoryTag = inventoryTagMapper.selectByIdForUpdate(inventoryTagId);
            if (inventoryTag == null) throw new BusinessException("库存标签不存在: " + inventoryTagId);
            if (!PRINTED.equals(inventoryTag.getStatus())) {
                throw new BusinessException("库存标签 %s 状态不允许取消".formatted(inventoryTag.getInventoryTagCode()));
            }
            InboundOrder order = inboundOrderMapper.selectById(inventoryTag.getInboundOrderId());
            if (!RELEASED.equals(order.getStatus()) && !PARTIAL_RECEIVED.equals(order.getStatus())) {
                throw new BusinessException("入库单 %s 状态不允许取消库存标签".formatted(order.getInboundNo()));
            }
            inventoryTag.setStatus(CANCELLED);
            inventoryTagMapper.updateById(inventoryTag);
            cancelled.add(inventoryTag);
            affectedLineIds.add(inventoryTag.getInboundOrderLineId());
            affectedOrderIds.add(inventoryTag.getInboundOrderId());
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
        List<InventoryTag> lineInventoryTags = inventoryTagMapper.selectList(Wrappers.<InventoryTag>lambdaQuery()
                .eq(InventoryTag::getInboundOrderLineId, lineId)
                .ne(InventoryTag::getStatus, CANCELLED));
        BigDecimal newPlannedQty = lineInventoryTags.stream()
                .map(InventoryTag::getBoardQty)
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

    private InventoryMovement createMovement(ScanInventoryTagContext context, LocalDateTime now) {
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementNo(generateMovementNo(now));
        movement.setMovementType(INBOUND_RECEIVE);
        movement.setSourceType(INVENTORY_TAG);
        movement.setSourceId(context.getInventoryTagId());
        movement.setInventoryTagId(context.getInventoryTagId());
        movement.setMaterialId(context.getMaterialId());
        movement.setPlannedLocationId(context.getTargetLocationId());
        movement.setWarehouseId(context.getTargetWarehouseId());
        movement.setStorageLocationId(context.getTargetLocationId());
        movement.setQty(context.getBoardQty());
        movement.setOccurredAt(now);
        movement.setOperatorName("web");
        return movement;
    }

    private InventoryBalance upsertBalance(ScanInventoryTagContext context, Long actualLocationId) {
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

    private InventoryTag requireInventoryTag(Long inventoryTagId) {
        InventoryTag board = inventoryTagMapper.selectById(inventoryTagId);
        if (board == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "库存标签不存在");
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
