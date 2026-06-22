package com.scut.wms.lock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
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
    private final KanbanBoardMapper kanbanBoardMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final MaterialMapper materialMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final InventoryLockMapper inventoryLockMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderLineMapper outboundOrderLineMapper;

    public InventoryHoldService(
            InventoryHoldMapper inventoryHoldMapper,
            KanbanBoardMapper kanbanBoardMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            MaterialMapper materialMapper,
            StorageLocationMapper storageLocationMapper,
            InventoryLockMapper inventoryLockMapper,
            OutboundOrderMapper outboundOrderMapper,
            OutboundOrderLineMapper outboundOrderLineMapper
    ) {
        this.inventoryHoldMapper = inventoryHoldMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.materialMapper = materialMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.inventoryLockMapper = inventoryLockMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
    }

    @Transactional
    public InventoryHoldView seal(Long kanbanId, HoldCommand command) {
        KanbanBoard board = requireEditableBoard(kanbanId, "封存");
        InventoryHold hold = createHold(board, InventoryHold.SEALED, command);
        board.setStatus(SEALED);
        kanbanBoardMapper.updateById(board);
        return toView(board, hold);
    }

    @Transactional
    public InventoryHoldView unseal(Long kanbanId, HoldCommand command) {
        KanbanBoard board = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
        if (board == null) {
            throw new BusinessException("看板不存在");
        }
        InventoryHold hold = requireActiveHold(kanbanId, InventoryHold.SEALED);
        releaseHold(hold, command);
        board.setStatus(RECEIVED);
        kanbanBoardMapper.updateById(board);
        return toView(board, hold);
    }

    @Transactional
    public InventoryHoldView manualLock(Long kanbanId, HoldCommand command) {
        KanbanBoard board = requireEditableBoard(kanbanId, "手动锁库");
        InventoryHold hold = createHold(board, InventoryHold.MANUAL_LOCK, command);
        return toView(board, hold);
    }

    @Transactional
    public InventoryHoldView manualUnlock(Long kanbanId, HoldCommand command) {
        KanbanBoard board = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
        if (board == null) {
            throw new BusinessException("看板不存在");
        }
        InventoryHold hold = requireActiveHold(kanbanId, InventoryHold.MANUAL_LOCK);
        releaseHold(hold, command);
        return toView(board, hold);
    }

    public List<InventoryHoldView> listHolds(String holdType, String status, String materialCode, String kanbanCode) {
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
                .filter(view -> !StringUtils.hasText(kanbanCode)
                        || (view.kanbanCode() != null && view.kanbanCode().contains(kanbanCode)))
                .collect(Collectors.toList());
    }

    public void ensureOutboundAllowed(KanbanBoard board, String holdType, String actionName) {
        if (SEALED.equals(board.getStatus()) || InventoryHold.SEALED.equals(holdType)) {
            throw new BusinessException("看板已封存，不能执行" + actionName);
        }
        if (InventoryHold.MANUAL_LOCK.equals(holdType)) {
            throw new BusinessException("看板已手动锁库，不能执行" + actionName);
        }
    }

    public void assertNormalFifoPick(Long outboundOrderId, Long outboundOrderLineId, Long kanbanBoardId) {
        if (outboundOrderId == null || outboundOrderLineId == null) {
            return;
        }
        List<InventoryLock> activeLocks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, outboundOrderId)
                        .eq(InventoryLock::getOutboundOrderLineId, outboundOrderLineId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        if (activeLocks.isEmpty()) {
            return;
        }
        List<KanbanBoard> candidates = activeLocks.stream()
                .map(lock -> kanbanBoardMapper.selectById(lock.getKanbanBoardId()))
                .filter(Objects::nonNull)
                .filter(board -> remainingQty(board).compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(KanbanBoard::getReceivedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(KanbanBoard::getId))
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        KanbanBoard expected = candidates.get(0);
        if (!Objects.equals(expected.getId(), kanbanBoardId)) {
            throw new BusinessException("FIFO 违规：请先出库更早入库的看板 " + expected.getKanbanCode());
        }
    }

    public InventoryHold findActiveHold(Long kanbanId) {
        return inventoryHoldMapper.selectOne(
                Wrappers.<InventoryHold>lambdaQuery()
                        .eq(InventoryHold::getKanbanBoardId, kanbanId)
                        .eq(InventoryHold::getStatus, InventoryHold.ACTIVE)
                        .last("LIMIT 1"));
    }

    public record HoldCommand(String reason, String remark, String operator) {}

    private KanbanBoard requireEditableBoard(Long kanbanId, String actionName) {
        KanbanBoard board = kanbanBoardMapper.selectByIdForUpdate(kanbanId);
        if (board == null) {
            throw new BusinessException("看板不存在");
        }
        if (board.getLockedByOrderId() != null || LOCKED.equals(board.getStatus())) {
            throw new BusinessException("看板已被出库单锁定，不能" + actionName);
        }
        if (!RECEIVED.equals(board.getStatus())) {
            throw new BusinessException("当前看板状态不允许" + actionName + ": " + board.getStatus());
        }
        if (remainingQty(board).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("看板剩余数量为 0，不能" + actionName);
        }
        InventoryHold active = findActiveHold(kanbanId);
        if (active != null) {
            throw new BusinessException("看板已有生效中的人工占用，不能重复" + actionName);
        }
        return board;
    }

    private InventoryHold createHold(KanbanBoard board, String holdType, HoldCommand command) {
        InventoryHold hold = new InventoryHold();
        hold.setKanbanBoardId(board.getId());
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

    private InventoryHold requireActiveHold(Long kanbanId, String holdType) {
        InventoryHold hold = inventoryHoldMapper.selectOne(
                Wrappers.<InventoryHold>lambdaQuery()
                        .eq(InventoryHold::getKanbanBoardId, kanbanId)
                        .eq(InventoryHold::getHoldType, holdType)
                        .eq(InventoryHold::getStatus, InventoryHold.ACTIVE)
                        .last("LIMIT 1"));
        if (hold == null) {
            throw new BusinessException("未找到生效中的" + label(holdType) + "记录");
        }
        return hold;
    }

    private InventoryHoldView toView(InventoryHold hold) {
        KanbanBoard board = kanbanBoardMapper.selectById(hold.getKanbanBoardId());
        return toView(board, hold);
    }

    private InventoryHoldView toView(KanbanBoard board, InventoryHold hold) {
        InboundOrderLine line = board == null ? null : inboundOrderLineMapper.selectById(board.getInboundOrderLineId());
        Material material = line == null ? null : materialMapper.selectById(line.getMaterialId());
        StorageLocation location = board == null ? null : storageLocationMapper.selectById(board.getLocationId());
        return new InventoryHoldView(
                hold.getId(),
                hold.getKanbanBoardId(),
                board == null ? null : board.getKanbanCode(),
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

    private BigDecimal remainingQty(KanbanBoard board) {
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
