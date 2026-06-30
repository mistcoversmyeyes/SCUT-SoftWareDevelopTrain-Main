package com.scut.wms.lock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
import com.scut.wms.inventory.InventoryTransactionMapper;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.masterdata.StorageLocation;
import com.scut.wms.masterdata.StorageLocationMapper;
import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderMapper;
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InventoryHoldService {
    private static final String RECEIVED = "RECEIVED";
    private static final String LOCKED = "LOCKED";
    private static final String SEALED = "SEALED";

    private final InventoryHoldMapper inventoryHoldMapper;
    private final InventoryTagMapper inventoryTagMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final MaterialMapper materialMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final InventoryLockMapper inventoryLockMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderLineMapper outboundOrderLineMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;

    public InventoryHoldService(
            InventoryHoldMapper inventoryHoldMapper,
            InventoryTagMapper inventoryTagMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            MaterialMapper materialMapper,
            StorageLocationMapper storageLocationMapper,
            InventoryLockMapper inventoryLockMapper,
            OutboundOrderMapper outboundOrderMapper,
            OutboundOrderLineMapper outboundOrderLineMapper,
            InventoryTransactionMapper inventoryTransactionMapper
    ) {
        this.inventoryHoldMapper = inventoryHoldMapper;
        this.inventoryTagMapper = inventoryTagMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.materialMapper = materialMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.inventoryLockMapper = inventoryLockMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
    }

    @Transactional
    public InventoryHoldView seal(Long inventoryTagId, HoldCommand command) {
        InventoryTag board = requireEditableBoard(inventoryTagId, "封存");
        InventoryHold hold = createHold(board, InventoryHold.SEALED, command);
        board.setStatus(SEALED);
        inventoryTagMapper.updateById(board);
        return toView(board, hold);
    }

    @Transactional
    public InventoryHoldView unseal(Long inventoryTagId, HoldCommand command) {
        InventoryTag board = inventoryTagMapper.selectByIdForUpdate(inventoryTagId);
        if (board == null) {
            throw new BusinessException("库存标签不存在");
        }
        InventoryHold hold = requireActiveHold(inventoryTagId, InventoryHold.SEALED);
        releaseHold(hold, command);
        board.setStatus(RECEIVED);
        inventoryTagMapper.updateById(board);
        return toView(board, hold);
    }

    @Transactional
    public InventoryHoldView manualLock(Long inventoryTagId, HoldCommand command) {
        InventoryTag board = requireEditableBoard(inventoryTagId, "手动锁库");
        InventoryHold hold = createHold(board, InventoryHold.MANUAL_LOCK, command);
        return toView(board, hold);
    }

    @Transactional
    public InventoryHoldView manualUnlock(Long inventoryTagId, HoldCommand command) {
        InventoryTag board = inventoryTagMapper.selectByIdForUpdate(inventoryTagId);
        if (board == null) {
            throw new BusinessException("库存标签不存在");
        }
        InventoryHold hold = requireActiveHold(inventoryTagId, InventoryHold.MANUAL_LOCK);
        releaseHold(hold, command);
        return toView(board, hold);
    }

    public List<InventoryHoldView> listHolds(String holdType, String status, String materialCode, String inventoryTagCode) {
        List<InventoryHold> holds = inventoryHoldMapper.selectList(
                Wrappers.<InventoryHold>lambdaQuery()
                        .eq(StringUtils.hasText(holdType), InventoryHold::getHoldType, holdType)
                        .eq(StringUtils.hasText(status), InventoryHold::getStatus, status)
                        .orderByDesc(InventoryHold::getCreatedAt)
                        .orderByDesc(InventoryHold::getId));
        return holds.stream()
                .map(this::toView)
                .filter(view -> !StringUtils.hasText(materialCode)
                        || (view.materialCode() != null && (Objects.equals(view.materialCode(), materialCode) || view.materialCode().contains(materialCode))))
                .filter(view -> !StringUtils.hasText(inventoryTagCode)
                        || (view.inventoryTagCode() != null && view.inventoryTagCode().contains(inventoryTagCode)))
                .collect(Collectors.toList());
    }

    public void ensureOrderOutboundAllowed(InventoryTag board) {
        ensureOutboundAllowed(board, false);
    }

    public void ensureNormalOutboundAllowed(InventoryTag board) {
        ensureOutboundAllowed(board, true);
    }

    public boolean hasBlockingAutoLockHold(Long inventoryTagId) {
        return hasActiveHold(inventoryTagId, InventoryHold.SEALED, InventoryHold.MANUAL_LOCK);
    }

    public void assertNormalFifoPick(Long outboundOrderId, Long outboundOrderLineId, Long inventoryTagId, Long materialId) {
        if (outboundOrderId == null || outboundOrderLineId == null) {
            return;
        }
        List<InventoryLock> activeLocks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, outboundOrderId)
                        .eq(InventoryLock::getOutboundOrderLineId, outboundOrderLineId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        if (!activeLocks.isEmpty()) {
            // Lock-based FIFO: check against locked tags
            List<InventoryTag> candidates = activeLocks.stream()
                    .map(lock -> inventoryTagMapper.selectById(lock.getInventoryTagId()))
                    .filter(Objects::nonNull)
                    .filter(board -> remainingQty(board).compareTo(BigDecimal.ZERO) > 0)
                    .sorted(Comparator.comparing(InventoryTag::getReceivedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                            .thenComparing(InventoryTag::getId))
                    .toList();
            if (!candidates.isEmpty()) {
                InventoryTag expected = candidates.get(0);
                if (!Objects.equals(expected.getId(), inventoryTagId)) {
                    throw new BusinessException("FIFO 违规：请先出库更早入库的库存标签 " + expected.getInventoryTagCode());
                }
            }
            return;
        }

        // No locks: check FIFO against all available RECEIVED tags for this material
        if (materialId == null) {
            return;
        }
        Long earliestFifoTagId = inventoryTransactionMapper.selectEarliestFifoTagId(materialId);
        if (earliestFifoTagId != null && !Objects.equals(earliestFifoTagId, inventoryTagId)) {
            InventoryTag expected = inventoryTagMapper.selectById(earliestFifoTagId);
            if (expected != null) {
                throw new BusinessException("FIFO 违规：请先出库更早入库的库存标签 " + expected.getInventoryTagCode());
            }
        }
    }

    public InventoryHold findActiveHold(Long inventoryTagId) {
        return inventoryHoldMapper.selectOne(
                Wrappers.<InventoryHold>lambdaQuery()
                        .eq(InventoryHold::getInventoryTagId, inventoryTagId)
                        .eq(InventoryHold::getStatus, InventoryHold.ACTIVE)
                        .last("LIMIT 1"));
    }

    public record HoldCommand(String reason, String remark, String operator) {}

    private InventoryTag requireEditableBoard(Long inventoryTagId, String actionName) {
        InventoryTag board = inventoryTagMapper.selectByIdForUpdate(inventoryTagId);
        if (board == null) {
            throw new BusinessException("库存标签不存在");
        }
        if (board.getLockedByOrderId() != null || LOCKED.equals(board.getStatus())) {
            throw new BusinessException("库存标签已被出库单锁定，不能" + actionName);
        }
        if (!RECEIVED.equals(board.getStatus())) {
            throw new BusinessException("当前库存标签状态不允许" + actionName + ": " + board.getStatus());
        }
        if (remainingQty(board).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("库存标签剩余数量为 0，不能" + actionName);
        }
        InventoryHold active = findActiveHold(inventoryTagId);
        if (active != null) {
            throw new BusinessException("库存标签已有生效中的人工占用，不能重复" + actionName);
        }
        return board;
    }

    private void ensureOutboundAllowed(InventoryTag board, boolean normalOutbound) {
        boolean sealed = SEALED.equals(board.getStatus()) || hasActiveHold(board.getId(), InventoryHold.SEALED);
        if (sealed) {
            throw new BusinessException(normalOutbound
                    ? "库存标签已封存，不能普通出库"
                    : "库存标签已封存，不能出库");
        }

        if (hasActiveHold(board.getId(), InventoryHold.MANUAL_LOCK)) {
            throw new BusinessException(normalOutbound
                    ? "库存标签已手动锁库，不能普通出库"
                    : "库存标签已手动锁库，不能出库");
        }
    }

    private InventoryHold createHold(InventoryTag board, String holdType, HoldCommand command) {
        InventoryHold hold = new InventoryHold();
        hold.setInventoryTagId(board.getId());
        hold.setHoldType(holdType);
        hold.setHoldQty(remainingQty(board));
        hold.setStatus(InventoryHold.ACTIVE);
        hold.setReason(command.reason());
        hold.setRemark(command.remark());
        hold.setOperatorName(operator(command.operator()));
        inventoryHoldMapper.insert(hold);
        return hold;
    }

    private void releaseHold(InventoryHold hold, HoldCommand command) {
        hold.setStatus(InventoryHold.RELEASED);
        hold.setReleasedReason(command.reason());
        hold.setReleasedRemark(command.remark());
        hold.setReleasedBy(operator(command.operator()));
        hold.setReleasedAt(LocalDateTime.now());
        inventoryHoldMapper.updateById(hold);
    }

    private InventoryHold requireActiveHold(Long inventoryTagId, String holdType) {
        InventoryHold hold = inventoryHoldMapper.selectOne(
                Wrappers.<InventoryHold>lambdaQuery()
                        .eq(InventoryHold::getInventoryTagId, inventoryTagId)
                        .eq(InventoryHold::getHoldType, holdType)
                        .eq(InventoryHold::getStatus, InventoryHold.ACTIVE)
                        .last("LIMIT 1"));
        if (hold == null) {
            throw new BusinessException("未找到生效中的" + label(holdType) + "记录");
        }
        return hold;
    }

    private boolean hasActiveHold(Long inventoryTagId, String... holdTypes) {
        var query = Wrappers.<InventoryHold>lambdaQuery()
                .eq(InventoryHold::getInventoryTagId, inventoryTagId)
                .eq(InventoryHold::getStatus, InventoryHold.ACTIVE);
        if (holdTypes.length == 1) {
            query.eq(InventoryHold::getHoldType, holdTypes[0]);
        } else {
            query.in(InventoryHold::getHoldType, Arrays.asList(holdTypes));
        }
        return inventoryHoldMapper.selectCount(query) > 0;
    }

    private InventoryHoldView toView(InventoryHold hold) {
        InventoryTag board = inventoryTagMapper.selectById(hold.getInventoryTagId());
        return toView(board, hold);
    }

    private InventoryHoldView toView(InventoryTag board, InventoryHold hold) {
        InboundOrderLine line = board == null ? null : inboundOrderLineMapper.selectById(board.getInboundOrderLineId());
        Material material = line == null ? null : materialMapper.selectById(line.getMaterialId());
        StorageLocation location = board == null ? null : storageLocationMapper.selectById(board.getLocationId());
        return new InventoryHoldView(
                hold.getId(),
                hold.getInventoryTagId(),
                board == null ? null : board.getInventoryTagCode(),
                material == null ? null : material.getMaterialCode(),
                material == null ? null : material.getMaterialName(),
                location == null ? null : location.getLocationName(),
                board == null ? null : board.getStatus(),
                hold.getHoldType(),
                hold.getHoldQty(),
                hold.getStatus(),
                hold.getReason(),
                hold.getRemark(),
                hold.getOperatorName(),
                hold.getReleasedReason(),
                hold.getReleasedRemark(),
                hold.getReleasedBy(),
                hold.getCreatedAt(),
                hold.getReleasedAt());
    }

    private BigDecimal remainingQty(InventoryTag board) {
        BigDecimal pickedQty = board.getPickedQty() == null ? BigDecimal.ZERO : board.getPickedQty();
        return board.getBoardQty().subtract(pickedQty);
    }

    private String operator(String operator) {
        return StringUtils.hasText(operator) ? operator : "web";
    }

    private String label(String holdType) {
        return InventoryHold.SEALED.equals(holdType) ? "封存" : "手动锁库";
    }
}
