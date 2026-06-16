package com.scut.wms.lock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inbound.KanbanBoard;
import com.scut.wms.inbound.KanbanBoardMapper;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inventory.InventoryTransactionMapper;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.masterdata.StorageLocation;
import com.scut.wms.masterdata.StorageLocationMapper;
import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import com.scut.wms.outbound.OutboundOrderMapper;
import com.scut.wms.outbound.picking.FifoPickCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LockService {
    private static final Logger log = LoggerFactory.getLogger(LockService.class);
    private static final String RECEIVED = "RECEIVED";
    private static final String LOCKED_KANBAN = "LOCKED";

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderLineMapper outboundOrderLineMapper;
    private final KanbanBoardMapper kanbanBoardMapper;
    private final InventoryLockMapper inventoryLockMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final MaterialMapper materialMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;

    public LockService(
            OutboundOrderMapper outboundOrderMapper,
            OutboundOrderLineMapper outboundOrderLineMapper,
            KanbanBoardMapper kanbanBoardMapper,
            InventoryLockMapper inventoryLockMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            MaterialMapper materialMapper,
            StorageLocationMapper storageLocationMapper,
            InboundOrderLineMapper inboundOrderLineMapper
    ) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.inventoryLockMapper = inventoryLockMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.materialMapper = materialMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
    }

    /**
     * 释放并加锁：对出库单的每条明细行按 FIFO 锁定看板。
     * warehouseIds 可选，限制锁定范围。
     */
    @Transactional
    public void releaseAndLock(Long orderId, List<Long> warehouseIds) {
        OutboundOrder order = outboundOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }
        if (!OutboundOrder.DRAFT.equals(order.getStatus())) {
            throw new BusinessException("只有草稿状态的出库单才能释放并加锁");
        }

        List<OutboundOrderLine> lines = outboundOrderLineMapper.selectList(
                Wrappers.<OutboundOrderLine>lambdaQuery()
                        .eq(OutboundOrderLine::getOutboundOrderId, orderId)
                        .orderByAsc(OutboundOrderLine::getLineNo));
        if (lines.isEmpty()) {
            throw new BusinessException("出库单明细不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        List<String> shortageWarnings = new ArrayList<>();

        for (OutboundOrderLine line : lines) {
            BigDecimal needed = line.getPlannedQty();
            List<FifoPickCandidate> candidates = inventoryTransactionMapper.selectFifoCandidatesForLock(
                    line.getMaterialId(), warehouseIds);

            BigDecimal lockedForLine = BigDecimal.ZERO;
            for (FifoPickCandidate candidate : candidates) {
                if (lockedForLine.compareTo(needed) >= 0) break;

                BigDecimal picked = candidate.getPickedQty() == null ? BigDecimal.ZERO : candidate.getPickedQty();
                BigDecimal remaining = candidate.getBoardQty().subtract(picked);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal toLock = needed.subtract(lockedForLine);
                if (toLock.compareTo(remaining) > 0) {
                    toLock = remaining;
                }

                KanbanBoard board = kanbanBoardMapper.selectById(candidate.getKanbanId());
                if (board == null || !RECEIVED.equals(board.getStatus())) continue;

                board.setStatus(LOCKED_KANBAN);
                board.setLockedByOrderId(orderId);
                board.setLockedByOrderLineId(line.getId());
                kanbanBoardMapper.updateById(board);

                InventoryLock lock = new InventoryLock();
                lock.setOutboundOrderId(orderId);
                lock.setOutboundOrderLineId(line.getId());
                lock.setKanbanBoardId(candidate.getKanbanId());
                lock.setMaterialId(line.getMaterialId());
                lock.setLockQty(toLock);
                lock.setStatus(InventoryLock.LOCKED);
                inventoryLockMapper.insert(lock);

                lockedForLine = lockedForLine.add(toLock);
            }

            if (lockedForLine.compareTo(needed) < 0) {
                shortageWarnings.add("物料行" + line.getLineNo() + " 缺 " + needed.subtract(lockedForLine));
            }
        }

        String qrCode = order.getOutboundNo();
        order.setStatus(OutboundOrder.LOCKED);
        order.setReleasedAt(now);
        order.setQrcode(qrCode);
        outboundOrderMapper.updateById(order);

        if (!shortageWarnings.isEmpty()) {
            log.warn("释放并加锁部分库存不足: orderId={}, warnings={}", orderId, shortageWarnings);
            throw new BusinessException("部分物料库存不足: " + String.join("; ", shortageWarnings));
        }
    }

    public List<LockOrderSummary> listLockOrders(String outboundNo, String materialCode, String status) {
        return inventoryLockMapper.selectLockOrderSummaries(outboundNo, materialCode, status);
    }

    public List<LockDetailView> listLockDetails(Long outboundOrderId) {
        return inventoryLockMapper.selectLockDetails(outboundOrderId);
    }

    public List<ForceLogView> listForceLogs(String outboundNo) {
        return inventoryLockMapper.selectForceLogs(outboundNo);
    }

    @Transactional
    public void unlock(Long lockId, String operator) {
        InventoryLock lock = inventoryLockMapper.selectById(lockId);
        if (lock == null) {
            throw new BusinessException("锁记录不存在");
        }
        if (!InventoryLock.LOCKED.equals(lock.getStatus())) {
            throw new BusinessException("锁记录当前状态不允许解锁: " + lock.getStatus());
        }

        KanbanBoard board = kanbanBoardMapper.selectById(lock.getKanbanBoardId());
        if (board != null && LOCKED_KANBAN.equals(board.getStatus())) {
            board.setStatus(RECEIVED);
            board.setLockedByOrderId(null);
            board.setLockedByOrderLineId(null);
            kanbanBoardMapper.updateById(board);
        }

        lock.setStatus(InventoryLock.RELEASED);
        lock.setUnlockedAt(LocalDateTime.now());
        lock.setUnlockedBy(operator);
        inventoryLockMapper.updateById(lock);

        syncOrderStatus(lock.getOutboundOrderId());
    }

    @Transactional
    public void reassign(Long orderId) {
        OutboundOrder order = outboundOrderMapper.selectByIdForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("出库单不存在");
        }

        // Release all existing locks
        releaseAllLocks(orderId);

        // Recalculate unpicked needs and re-lock via FIFO
        List<OutboundOrderLine> lines = outboundOrderLineMapper.selectList(
                Wrappers.<OutboundOrderLine>lambdaQuery()
                        .eq(OutboundOrderLine::getOutboundOrderId, orderId)
                        .orderByAsc(OutboundOrderLine::getLineNo));

        for (OutboundOrderLine line : lines) {
            BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
            BigDecimal stillNeeded = line.getPlannedQty().subtract(picked);
            if (stillNeeded.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<FifoPickCandidate> candidates = inventoryTransactionMapper.selectFifoCandidatesForLock(
                    line.getMaterialId(), null);

            BigDecimal lockedForLine = BigDecimal.ZERO;
            for (FifoPickCandidate candidate : candidates) {
                if (lockedForLine.compareTo(stillNeeded) >= 0) break;

                BigDecimal cPicked = candidate.getPickedQty() == null ? BigDecimal.ZERO : candidate.getPickedQty();
                BigDecimal remaining = candidate.getBoardQty().subtract(cPicked);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                BigDecimal toLock = stillNeeded.subtract(lockedForLine);
                if (toLock.compareTo(remaining) > 0) toLock = remaining;

                KanbanBoard board = kanbanBoardMapper.selectById(candidate.getKanbanId());
                if (board == null || !RECEIVED.equals(board.getStatus())) continue;

                board.setStatus(LOCKED_KANBAN);
                board.setLockedByOrderId(orderId);
                board.setLockedByOrderLineId(line.getId());
                kanbanBoardMapper.updateById(board);

                InventoryLock lock = new InventoryLock();
                lock.setOutboundOrderId(orderId);
                lock.setOutboundOrderLineId(line.getId());
                lock.setKanbanBoardId(candidate.getKanbanId());
                lock.setMaterialId(line.getMaterialId());
                lock.setLockQty(toLock);
                lock.setStatus(InventoryLock.LOCKED);
                inventoryLockMapper.insert(lock);

                lockedForLine = lockedForLine.add(toLock);
            }
        }
    }

    /**
     * 取消出库单时释放所有锁。
     */
    @Transactional
    public void releaseOrderLocks(Long orderId) {
        releaseAllLocks(orderId);
        // Also reset kanban statuses
        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, orderId));
        for (InventoryLock lock : locks) {
            KanbanBoard board = kanbanBoardMapper.selectById(lock.getKanbanBoardId());
            if (board != null && LOCKED_KANBAN.equals(board.getStatus())) {
                board.setStatus(RECEIVED);
                board.setLockedByOrderId(null);
                board.setLockedByOrderLineId(null);
                kanbanBoardMapper.updateById(board);
            }
        }
    }

    /**
     * 带单强制出库时抢锁：将看板的锁从原单转给本单。
     */
    @Transactional
    public void stealLockForOrder(Long orderId, Long kanbanBoardId) {
        List<InventoryLock> existingLocks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getKanbanBoardId, kanbanBoardId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        for (InventoryLock lock : existingLocks) {
            lock.setStatus(InventoryLock.FORCE_STOLEN);
            lock.setStolenByOrderId(orderId);
            lock.setStolenAt(LocalDateTime.now());
            inventoryLockMapper.updateById(lock);
        }

        KanbanBoard board = kanbanBoardMapper.selectById(kanbanBoardId);
        if (board != null) {
            board.setLockedByOrderId(orderId);
            kanbanBoardMapper.updateById(board);
        }
    }

    /**
     * 不带单出库时标记看板锁为被抢。
     */
    @Transactional
    public void markForceStolen(Long kanbanBoardId) {
        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getKanbanBoardId, kanbanBoardId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        for (InventoryLock lock : locks) {
            lock.setStatus(InventoryLock.FORCE_STOLEN);
            lock.setStolenAt(LocalDateTime.now());
            inventoryLockMapper.updateById(lock);
        }
    }

    private void releaseAllLocks(Long orderId) {
        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, orderId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        for (InventoryLock lock : locks) {
            KanbanBoard board = kanbanBoardMapper.selectById(lock.getKanbanBoardId());
            if (board != null && LOCKED_KANBAN.equals(board.getStatus())) {
                board.setStatus(RECEIVED);
                board.setLockedByOrderId(null);
                board.setLockedByOrderLineId(null);
                kanbanBoardMapper.updateById(board);
            }
            lock.setStatus(InventoryLock.RELEASED);
            lock.setUnlockedAt(LocalDateTime.now());
            lock.setUnlockedBy("system");
            inventoryLockMapper.updateById(lock);
        }
    }

    private void syncOrderStatus(Long orderId) {
        List<InventoryLock> activeLocks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, orderId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        if (activeLocks.isEmpty()) {
            OutboundOrder order = outboundOrderMapper.selectById(orderId);
            if (order != null && OutboundOrder.LOCKED.equals(order.getStatus())) {
                order.setStatus(OutboundOrder.DRAFT);
                outboundOrderMapper.updateById(order);
            }
        }
    }

    /**
     * 获取强制出库候选看板：该出库单每条明细行的锁定看板 + RECEIVED 空闲看板。
     */
    public ForceCandidateResponse getForceCandidates(Long orderId) {
        List<OutboundOrderLine> lines = outboundOrderLineMapper.selectList(
                Wrappers.<OutboundOrderLine>lambdaQuery()
                        .eq(OutboundOrderLine::getOutboundOrderId, orderId)
                        .orderByAsc(OutboundOrderLine::getLineNo));

        List<ForceCandidateResponse.LineCandidates> result = new ArrayList<>();

        for (OutboundOrderLine line : lines) {
            Material material = materialMapper.selectById(line.getMaterialId());
            if (material == null) continue;

            List<ForceCandidateResponse.KanbanEntry> kanbans = new ArrayList<>();

            // Locked kanbans for this order
            List<KanbanBoard> lockedKanbans = kanbanBoardMapper.selectList(
                    Wrappers.<KanbanBoard>lambdaQuery()
                            .eq(KanbanBoard::getLockedByOrderId, orderId)
                            .eq(KanbanBoard::getLockedByOrderLineId, line.getId()));
            for (KanbanBoard kb : lockedKanbans) {
                String locName = getLocationName(kb);
                kanbans.add(new ForceCandidateResponse.KanbanEntry(
                        kb.getId(), kb.getKanbanCode(), locName,
                        kb.getBoardQty(), true, null));
            }

            // RECEIVED kanbans for same material (FIFO order)
            List<FifoPickCandidate> candidates = inventoryTransactionMapper.selectFifoCandidatesForLock(
                    line.getMaterialId(), null);
            for (FifoPickCandidate c : candidates) {
                boolean alreadyListed = lockedKanbans.stream()
                        .anyMatch(kb -> kb.getId().equals(c.getKanbanId()));
                if (alreadyListed) continue;

                KanbanBoard kb = kanbanBoardMapper.selectById(c.getKanbanId());
                if (kb == null) continue;
                String locName = getLocationName(kb);
                BigDecimal remaining = c.getBoardQty().subtract(
                        c.getPickedQty() == null ? BigDecimal.ZERO : c.getPickedQty());
                kanbans.add(new ForceCandidateResponse.KanbanEntry(
                        kb.getId(), kb.getKanbanCode(), locName,
                        remaining, false, null));
            }

            result.add(new ForceCandidateResponse.LineCandidates(
                    line.getLineNo(),
                    line.getMaterialId(),
                    material.getMaterialCode(),
                    material.getMaterialName(),
                    line.getPlannedQty(),
                    kanbans));
        }

        return new ForceCandidateResponse(result);
    }

    private String getLocationName(KanbanBoard kb) {
        InboundOrderLine iol = inboundOrderLineMapper.selectById(kb.getInboundOrderLineId());
        if (iol != null && iol.getTargetLocationId() != null) {
            StorageLocation sl = storageLocationMapper.selectById(iol.getTargetLocationId());
            if (sl != null) return sl.getLocationName();
        }
        return "—";
    }
}
