package com.scut.wms.lock;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.container.ContainerType;
import com.scut.wms.container.ContainerTypeMapper;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class LockService {
    private static final Logger log = LoggerFactory.getLogger(LockService.class);
    private static final String RECEIVED = "RECEIVED";
    private static final String LOCKED_INVENTORY_TAG = "LOCKED";

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderLineMapper outboundOrderLineMapper;
    private final InventoryTagMapper inventoryTagMapper;
    private final InventoryLockMapper inventoryLockMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final MaterialMapper materialMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final ContainerTypeMapper containerTypeMapper;
    private final InventoryHoldService inventoryHoldService;

    public LockService(
            OutboundOrderMapper outboundOrderMapper,
            OutboundOrderLineMapper outboundOrderLineMapper,
            InventoryTagMapper inventoryTagMapper,
            InventoryLockMapper inventoryLockMapper,
            InventoryTransactionMapper inventoryTransactionMapper,
            MaterialMapper materialMapper,
            StorageLocationMapper storageLocationMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            ContainerTypeMapper containerTypeMapper,
            InventoryHoldService inventoryHoldService
    ) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.inventoryTagMapper = inventoryTagMapper;
        this.inventoryLockMapper = inventoryLockMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.materialMapper = materialMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.containerTypeMapper = containerTypeMapper;
        this.inventoryHoldService = inventoryHoldService;
    }

    /**
     * 释放并加锁：对出库单的每条明细行按 FIFO 锁定库存标签。
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
                    line.getMaterialId(), warehouseIds, line.getContainerTypeId());

            BigDecimal lockedForLine = BigDecimal.ZERO;
            for (FifoPickCandidate candidate : candidates) {
                if (lockedForLine.compareTo(needed) >= 0) break;

                BigDecimal picked = candidate.getPickedQty() == null ? BigDecimal.ZERO : candidate.getPickedQty();
                BigDecimal remaining = candidate.getBoardQty().subtract(picked);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Whole-inventory-tag locking: lock the FULL remaining, never split
                BigDecimal toLock = remaining;

                InventoryTag board = inventoryTagMapper.selectById(candidate.getInventoryTagId());
                if (board == null
                        || !RECEIVED.equals(board.getStatus())
                        || inventoryHoldService.hasBlockingAutoLockHold(board.getId())) {
                    continue;
                }

                board.setStatus(LOCKED_INVENTORY_TAG);
                board.setLockedByOrderId(orderId);
                board.setLockedByOrderLineId(line.getId());
                inventoryTagMapper.updateById(board);

                InventoryLock lock = new InventoryLock();
                lock.setOutboundOrderId(orderId);
                lock.setOutboundOrderLineId(line.getId());
                lock.setInventoryTagId(candidate.getInventoryTagId());
                lock.setMaterialId(line.getMaterialId());
                lock.setLockQty(toLock);
                lock.setStatus(InventoryLock.LOCKED);
                inventoryLockMapper.insert(lock);

                lockedForLine = lockedForLine.add(toLock);
            }

            if (lockedForLine.compareTo(needed) < 0) {
                Material mat = materialMapper.selectById(line.getMaterialId());
                ContainerType ct = line.getContainerTypeId() != null ? containerTypeMapper.selectById(line.getContainerTypeId()) : null;
                int capacity = ct != null && ct.getCapacityQty() != null ? ct.getCapacityQty().intValue() : 0;
                BigDecimal shortfall = needed.subtract(lockedForLine);
                String matInfo = mat != null ? mat.getMaterialCode() + " " + mat.getMaterialName() : "行" + line.getLineNo();
                String ctInfo = ct != null ? ct.getContainerName() : "未知容器";
                if (capacity > 0 && needed.compareTo(BigDecimal.ZERO) > 0) {
                    int neededBoxes = needed.intValue() / capacity;
                    int availableBoxes = lockedForLine.intValue() / capacity;
                    shortageWarnings.add(matInfo + ": 需要 " + neededBoxes + " 箱 " + ctInfo
                            + " (" + needed.intValue() + " 件)，只能锁 " + availableBoxes + " 箱 ("
                            + lockedForLine.intValue() + " 件)");
                } else {
                    shortageWarnings.add(matInfo + ": 短少 " + shortfall.intValue() + " 件");
                }
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

    public List<InventoryTagLockView> listInventoryTagLocks(String status, String materialCode, String outboundNo) {
        return inventoryLockMapper.selectInventoryTagLocks(status, materialCode, outboundNo);
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

        InventoryTag board = inventoryTagMapper.selectById(lock.getInventoryTagId());
        if (board != null && LOCKED_INVENTORY_TAG.equals(board.getStatus())) {
            board.setStatus(RECEIVED);
            board.setLockedByOrderId(null);
            board.setLockedByOrderLineId(null);
            inventoryTagMapper.updateById(board);
        }

        lock.setStatus(InventoryLock.RELEASED);
        lock.setUnlockedAt(LocalDateTime.now());
        lock.setUnlockedBy(operator);
        inventoryLockMapper.updateById(lock);

        // Re-lock FIFO replacements for the order that lost this inventoryTag
        reassignOrder(lock.getOutboundOrderId());
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
        reassignOrder(orderId);
    }

    /**
     * 对指定出库单执行 FIFO 补锁：检查每行已锁数量 vs 计划数量，
     * 不足的通过 FIFO 查找新库存标签锁定（不释放已有锁）。
     */
    private void reassignOrder(Long orderId) {
        List<OutboundOrderLine> lines = outboundOrderLineMapper.selectList(
                Wrappers.<OutboundOrderLine>lambdaQuery()
                        .eq(OutboundOrderLine::getOutboundOrderId, orderId)
                        .orderByAsc(OutboundOrderLine::getLineNo));

        for (OutboundOrderLine line : lines) {
            BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
            BigDecimal stillNeeded = line.getPlannedQty().subtract(picked);
            if (stillNeeded.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Check how much is already locked for this line
            List<InventoryLock> existingLocks = inventoryLockMapper.selectList(
                    Wrappers.<InventoryLock>lambdaQuery()
                            .eq(InventoryLock::getOutboundOrderId, orderId)
                            .eq(InventoryLock::getOutboundOrderLineId, line.getId())
                            .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
            BigDecimal alreadyLocked = existingLocks.stream()
                    .map(InventoryLock::getLockQty)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // stillNeeded is total planned minus picked; subtract what's already locked
            BigDecimal needed = stillNeeded.subtract(alreadyLocked);
            if (needed.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<FifoPickCandidate> candidates = inventoryTransactionMapper.selectFifoCandidatesForLock(
                    line.getMaterialId(), null, line.getContainerTypeId());

            BigDecimal lockedForLine = BigDecimal.ZERO;
            for (FifoPickCandidate candidate : candidates) {
                if (lockedForLine.compareTo(needed) >= 0) break;

                BigDecimal cPicked = candidate.getPickedQty() == null ? BigDecimal.ZERO : candidate.getPickedQty();
                BigDecimal remaining = candidate.getBoardQty().subtract(cPicked);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Skip inventoryTags already locked by this order
                boolean already = existingLocks.stream().anyMatch(l -> l.getInventoryTagId().equals(candidate.getInventoryTagId()));
                if (already) continue;

                // Whole-inventory-tag locking
                BigDecimal toLock = remaining;

                InventoryTag board = inventoryTagMapper.selectById(candidate.getInventoryTagId());
                if (board == null
                        || !RECEIVED.equals(board.getStatus())
                        || inventoryHoldService.hasBlockingAutoLockHold(board.getId())) {
                    continue;
                }

                board.setStatus(LOCKED_INVENTORY_TAG);
                board.setLockedByOrderId(orderId);
                board.setLockedByOrderLineId(line.getId());
                inventoryTagMapper.updateById(board);

                InventoryLock lock = new InventoryLock();
                lock.setOutboundOrderId(orderId);
                lock.setOutboundOrderLineId(line.getId());
                lock.setInventoryTagId(candidate.getInventoryTagId());
                lock.setMaterialId(line.getMaterialId());
                lock.setLockQty(toLock);
                lock.setStatus(InventoryLock.LOCKED);
                inventoryLockMapper.insert(lock);

                lockedForLine = lockedForLine.add(toLock);
            }

            // Update order status back to LOCKED if we locked more
            if (lockedForLine.compareTo(BigDecimal.ZERO) > 0) {
                OutboundOrder order = outboundOrderMapper.selectById(orderId);
                if (order != null && !OutboundOrder.LOCKED.equals(order.getStatus())) {
                    order.setStatus(OutboundOrder.LOCKED);
                    outboundOrderMapper.updateById(order);
                }
            }
        }
    }

    /**
     * 取消出库单时释放所有锁。
     */
    @Transactional
    public void releaseOrderLocks(Long orderId) {
        releaseAllLocks(orderId);
        // Also reset inventory tag statuses
        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, orderId));
        for (InventoryLock lock : locks) {
            InventoryTag board = inventoryTagMapper.selectById(lock.getInventoryTagId());
            if (board != null && LOCKED_INVENTORY_TAG.equals(board.getStatus())) {
                board.setStatus(RECEIVED);
                board.setLockedByOrderId(null);
                board.setLockedByOrderLineId(null);
                inventoryTagMapper.updateById(board);
            }
        }
    }

    /**
     * 带单强制出库时抢锁：将库存标签的锁从原单转给本单。
     */
    @Transactional
    public void stealLockForOrder(Long orderId, Long inventoryTagId) {
        Long victimOrderId = null;
        List<InventoryLock> existingLocks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getInventoryTagId, inventoryTagId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        for (InventoryLock lock : existingLocks) {
            if (lock.getOutboundOrderId() != null && !orderId.equals(lock.getOutboundOrderId())) {
                victimOrderId = lock.getOutboundOrderId();
            }
            lock.setStatus(InventoryLock.FORCE_STOLEN);
            lock.setStolenByOrderId(orderId);
            lock.setStolenAt(LocalDateTime.now());
            inventoryLockMapper.updateById(lock);
        }

        InventoryTag board = inventoryTagMapper.selectById(inventoryTagId);
        if (board != null) {
            board.setLockedByOrderId(orderId);
            inventoryTagMapper.updateById(board);
        }

        // Re-lock FIFO replacements for the victim order
        if (victimOrderId != null) {
            reassignOrder(victimOrderId);
        }
    }

    /**
     * 不带单出库时标记库存标签锁为被抢。
     */
    @Transactional
    public void markForceStolen(Long inventoryTagId) {
        Long victimOrderId = null;
        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getInventoryTagId, inventoryTagId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        for (InventoryLock lock : locks) {
            if (lock.getOutboundOrderId() != null) victimOrderId = lock.getOutboundOrderId();
            lock.setStatus(InventoryLock.FORCE_STOLEN);
            lock.setStolenAt(LocalDateTime.now());
            inventoryLockMapper.updateById(lock);
        }
        if (victimOrderId != null) {
            reassignOrder(victimOrderId);
        }
    }

    /**
     * 强制出库时创建审计记录（含 RECEIVED 库存标签被强制出库的场景）。
     * 在 inventory_lock 表中写入一条 FORCE_STOLEN 记录供审计查询。
     */
    @Transactional
    public void createForceAudit(Long outboundOrderId, com.scut.wms.inventory.ScanInventoryTagContext ctx) {
        Long lineId = null;
        if (outboundOrderId != null) {
            var lines = outboundOrderLineMapper.selectList(Wrappers.<OutboundOrderLine>lambdaQuery()
                    .eq(OutboundOrderLine::getOutboundOrderId, outboundOrderId)
                    .orderByAsc(OutboundOrderLine::getLineNo));
            if (!lines.isEmpty()) lineId = lines.get(0).getId();
        }
        InventoryLock lock = new InventoryLock();
        lock.setOutboundOrderId(outboundOrderId);
        lock.setOutboundOrderLineId(lineId);
        lock.setInventoryTagId(ctx.getInventoryTagId());
        lock.setMaterialId(ctx.getMaterialId());
        lock.setLockQty(ctx.getBoardQty());
        lock.setStatus(InventoryLock.FORCE_STOLEN);
        lock.setStolenByOrderId(outboundOrderId);
        lock.setStolenAt(LocalDateTime.now());
        inventoryLockMapper.insert(lock);
    }

    private void releaseAllLocks(Long orderId) {
        List<InventoryLock> locks = inventoryLockMapper.selectList(
                Wrappers.<InventoryLock>lambdaQuery()
                        .eq(InventoryLock::getOutboundOrderId, orderId)
                        .eq(InventoryLock::getStatus, InventoryLock.LOCKED));
        for (InventoryLock lock : locks) {
            InventoryTag board = inventoryTagMapper.selectById(lock.getInventoryTagId());
            if (board != null && LOCKED_INVENTORY_TAG.equals(board.getStatus())) {
                board.setStatus(RECEIVED);
                board.setLockedByOrderId(null);
                board.setLockedByOrderLineId(null);
                inventoryTagMapper.updateById(board);
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
     * 获取强制出库候选库存标签：同物料+同容器类型的所有 RECEIVED 库存标签，
     * 加上被其他出库单锁定的库存标签（可抢），排除已锁给本单的。
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

            List<ForceCandidateResponse.InventoryTagEntry> inventoryTags = new ArrayList<>();
            Set<Long> excludedIds = new HashSet<>();

            // Exclude inventoryTags already locked by THIS order
            List<InventoryTag> myLocked = inventoryTagMapper.selectList(
                    Wrappers.<InventoryTag>lambdaQuery()
                            .eq(InventoryTag::getLockedByOrderId, orderId)
                            .eq(InventoryTag::getLockedByOrderLineId, line.getId()));
            myLocked.forEach(kb -> excludedIds.add(kb.getId()));

            // FIFO RECEIVED inventoryTags (same material + container type)
            List<FifoPickCandidate> candidates = inventoryTransactionMapper.selectFifoCandidatesForLock(
                    line.getMaterialId(), null, line.getContainerTypeId());
            for (FifoPickCandidate c : candidates) {
                if (excludedIds.contains(c.getInventoryTagId())) continue;
                InventoryTag kb = inventoryTagMapper.selectById(c.getInventoryTagId());
                if (kb == null) continue;
                String locName = getLocationName(kb);
                BigDecimal remaining = c.getBoardQty().subtract(
                        c.getPickedQty() == null ? BigDecimal.ZERO : c.getPickedQty());
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;
                inventoryTags.add(new ForceCandidateResponse.InventoryTagEntry(
                        kb.getId(), kb.getInventoryTagCode(), locName,
                        remaining, false, null));
                excludedIds.add(c.getInventoryTagId());
            }

            // LOCKED inventoryTags from OTHER orders (same material + container type) — stealable
            List<InventoryTag> otherLocked = inventoryTagMapper.selectList(
                    Wrappers.<InventoryTag>lambdaQuery()
                            .eq(InventoryTag::getStatus, "LOCKED")
                            .eq(InventoryTag::getContainerTypeId, line.getContainerTypeId())
                            .ne(InventoryTag::getLockedByOrderId, orderId)
                            .isNotNull(InventoryTag::getLockedByOrderId));
            for (InventoryTag kb : otherLocked) {
                // Only if same material (via inbound_order_line)
                com.scut.wms.inbound.InboundOrderLine iol = inboundOrderLineMapper.selectById(kb.getInboundOrderLineId());
                if (iol == null || !iol.getMaterialId().equals(line.getMaterialId())) continue;
                if (excludedIds.contains(kb.getId())) continue;

                String locName = getLocationName(kb);
                BigDecimal remaining = kb.getBoardQty().subtract(
                        kb.getPickedQty() == null ? BigDecimal.ZERO : kb.getPickedQty());
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) continue;

                OutboundOrder otherOrder = outboundOrderMapper.selectById(kb.getLockedByOrderId());
                String otherOutboundNo = otherOrder != null ? otherOrder.getOutboundNo() : null;
                inventoryTags.add(new ForceCandidateResponse.InventoryTagEntry(
                        kb.getId(), kb.getInventoryTagCode(), locName,
                        remaining, true, otherOutboundNo));
            }

            result.add(new ForceCandidateResponse.LineCandidates(
                    line.getLineNo(),
                    line.getMaterialId(),
                    material.getMaterialCode(),
                    material.getMaterialName(),
                    line.getPlannedQty(),
                    inventoryTags));
        }

        return new ForceCandidateResponse(result);
    }

    private String getLocationName(InventoryTag kb) {
        InboundOrderLine iol = inboundOrderLineMapper.selectById(kb.getInboundOrderLineId());
        if (iol != null && iol.getTargetLocationId() != null) {
            StorageLocation sl = storageLocationMapper.selectById(iol.getTargetLocationId());
            if (sl != null) return sl.getLocationName();
        }
        return "—";
    }
}
