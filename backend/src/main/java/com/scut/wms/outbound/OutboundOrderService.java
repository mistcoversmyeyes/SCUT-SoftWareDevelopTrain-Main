package com.scut.wms.outbound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.container.ContainerType;
import com.scut.wms.container.ContainerTypeMapper;
import com.scut.wms.lock.LockService;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.masterdata.StorageLocation;
import com.scut.wms.masterdata.StorageLocationMapper;
import com.scut.wms.masterdata.Supplier;
import com.scut.wms.masterdata.SupplierMapper;
import com.scut.wms.masterdata.Warehouse;
import com.scut.wms.masterdata.WarehouseMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OutboundOrderService {
    private static final String ENABLED = "ENABLED";
    private static final String DRAFT = "DRAFT";
    private static final String RELEASED = "RELEASED";
    private static final String PICKING = "PICKING";
    private static final String PARTIAL_SHIPPED = "PARTIAL_SHIPPED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final DateTimeFormatter OUTBOUND_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderLineMapper outboundOrderLineMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final ContainerTypeMapper containerTypeMapper;
    private final LockService lockService;

    public OutboundOrderService(
            OutboundOrderMapper outboundOrderMapper,
            OutboundOrderLineMapper outboundOrderLineMapper,
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper,
            ContainerTypeMapper containerTypeMapper,
            LockService lockService
    ) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.containerTypeMapper = containerTypeMapper;
        this.lockService = lockService;
    }

    public List<OutboundOrderResponse> list(String status, String outboundNo, Long supplierId) {
        LambdaQueryWrapper<OutboundOrder> query = Wrappers.<OutboundOrder>lambdaQuery()
                .in(StringUtils.hasText(status), OutboundOrder::getStatus, splitStatus(status))
                .like(StringUtils.hasText(outboundNo), OutboundOrder::getOutboundNo, outboundNo)
                .apply(supplierId != null,
                        "EXISTS (SELECT 1 FROM outbound_order_line l WHERE l.outbound_order_id = outbound_order.id AND l.supplier_id = {0})",
                        supplierId)
                .orderByDesc(OutboundOrder::getCreatedAt)
                .orderByDesc(OutboundOrder::getId);
        return outboundOrderMapper.selectList(query).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OutboundOrderResponse create(OutboundOrderRequest request) {
        validateRequest(request);

        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo(generateOutboundNo());
        order.setSupplierId(request.lines().isEmpty() ? null : request.lines().get(0).supplierId());
        order.setPurpose(request.purpose());
        order.setSourceDocNo(request.sourceDocNo());
        order.setStatus(DRAFT);
        order.setRemark(request.remark());
        outboundOrderMapper.insert(order);

        insertLines(order.getId(), request.lines());
        return toResponse(order.getId());
    }

    @Transactional
    public OutboundOrderResponse update(Long id, OutboundOrderRequest request) {
        validateRequest(request);
        OutboundOrder order = requireOrder(id);
        if (DRAFT.equals(order.getStatus())) {
            replaceOrder(order, request);
            return toResponse(id);
        }
        if (RELEASED.equals(order.getStatus())) {
            if (hasPicked(id)) {
                throw new BusinessException("已有拣货记录的出库单不能修改");
            }
            replaceOrder(order, request);
            return toResponse(id);
        }
        throw new BusinessException("当前状态不允许修改出库单");
    }

    @Transactional
    public OutboundOrderResponse release(Long id) {
        OutboundOrder order = requireLockedOrder(id);
        if (RELEASED.equals(order.getStatus())) {
            return toResponse(id);
        }
        if (!DRAFT.equals(order.getStatus())) {
            throw new BusinessException("当前状态不允许释放出库单");
        }
        List<OutboundOrderLine> lines = linesOf(id);
        if (lines.isEmpty()) {
            throw new BusinessException("出库单明细不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        order.setStatus(RELEASED);
        order.setReleasedAt(now);
        outboundOrderMapper.updateById(order);
        return toResponse(id);
    }

    @Transactional
    public OutboundOrderResponse cancel(Long id) {
        OutboundOrder order = requireLockedOrder(id);
        if (CANCELLED.equals(order.getStatus())) {
            return toResponse(id);
        }
        if (COMPLETED.equals(order.getStatus()) || hasPicked(id)) {
            throw new BusinessException("已有拣货记录的出库单不能取消");
        }
        // Release all locks when cancelling a LOCKED order
        if (OutboundOrder.LOCKED.equals(order.getStatus()) || DRAFT.equals(order.getStatus())) {
            // LockService.releaseOrderLocks will be called by the controller if LOCKED
        }
        order.setStatus(CANCELLED);
        outboundOrderMapper.updateById(order);
        return toResponse(id);
    }

    @Transactional
    public OutboundOrderResponse startPicking(Long id) {
        OutboundOrder order = requireLockedOrder(id);
        if (!RELEASED.equals(order.getStatus())) {
            throw new BusinessException("只有已释放的出库单才能开始拣货");
        }
        order.setStatus(PICKING);
        outboundOrderMapper.updateById(order);
        return toResponse(id);
    }

    @Transactional
    public OutboundOrderResponse suspendPicking(Long id) {
        OutboundOrder order = requireLockedOrder(id);
        if (!PICKING.equals(order.getStatus())) {
            throw new BusinessException("只有拣货中的出库单才能挂起");
        }
        order.setStatus(RELEASED);
        outboundOrderMapper.updateById(order);
        return toResponse(id);
    }

    @Transactional
    public void addPickedQty(Long orderId, Long lineId, BigDecimal qty) {
        OutboundOrderLine line = outboundOrderLineMapper.selectByIdForUpdate(lineId);
        if (line == null || !line.getOutboundOrderId().equals(orderId)) {
            throw new BusinessException("出库单明细行不存在");
        }
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("拣货数量必须大于 0");
        }
        OutboundOrder order = requireLockedOrder(orderId);
        if (!PICKING.equals(order.getStatus()) && !RELEASED.equals(order.getStatus())
                && !OutboundOrder.LOCKED.equals(order.getStatus())) {
            throw new BusinessException("出库单状态不允许拣货，当前状态: " + order.getStatus());
        }
        // 确保状态为 PICKING
        if (RELEASED.equals(order.getStatus()) || OutboundOrder.LOCKED.equals(order.getStatus())) {
            order.setStatus(PICKING);
            outboundOrderMapper.updateById(order);
        }
        BigDecimal current = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
        BigDecimal newPicked = current.add(qty);
        if (line.getPlannedQty() != null && newPicked.compareTo(line.getPlannedQty()) > 0) {
            throw new BusinessException("拣货数量不能超过计划数量");
        }
        line.setPickedQty(newPicked);
        outboundOrderLineMapper.updateById(line);
        // 检查是否全部完成
        checkAndCompleteOrder(orderId);
    }

    @Transactional
    public void checkAndCompleteOrder(Long orderId) {
        List<OutboundOrderLine> lines = linesOf(orderId);
        boolean allDone = lines.stream()
                .allMatch(line -> line.getPickedQty() != null
                        && line.getPickedQty().compareTo(line.getPlannedQty()) >= 0);
        if (allDone) {
            OutboundOrder order = requireOrder(orderId);
            order.setStatus(COMPLETED);
            order.setCompletedAt(LocalDateTime.now());
            outboundOrderMapper.updateById(order);
            // Release all remaining locked inventoryTags — order is done, none needed anymore
            lockService.releaseOrderLocks(orderId);
        }
    }

    public String getOrderStatus(Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        return order != null ? order.getStatus() : null;
    }

    public OutboundPrintResponse print(Long id) {
        requireOrder(id);
        OutboundPrintHeader header = outboundOrderMapper.selectPrintHeader(id);
        List<OutboundPrintLine> lines = outboundOrderMapper.selectPrintLines(id);
        return new OutboundPrintResponse(
                header.id(),
                header.outboundNo(),
                header.supplierCode(),
                header.supplierName(),
                header.purpose(),
                header.sourceDocNo(),
                header.status(),
                header.remark(),
                header.releasedAt(),
                lines
        );
    }
    public OutboundOrderResponse getById(Long id) {
        return toResponse(requireOrder(id));
    }

    public OutboundOrderResponse getByOutboundNo(String outboundNo) {
        OutboundOrder order = outboundOrderMapper.selectOne(
                Wrappers.<OutboundOrder>lambdaQuery()
                        .eq(OutboundOrder::getOutboundNo, outboundNo));
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "出库单不存在: " + outboundNo);
        }
        return toResponse(order);
    }

    private void replaceOrder(OutboundOrder order, OutboundOrderRequest request) {
        order.setSupplierId(request.lines().isEmpty() ? null : request.lines().get(0).supplierId());
        order.setPurpose(request.purpose());
        order.setSourceDocNo(request.sourceDocNo());
        order.setRemark(request.remark());
        outboundOrderMapper.updateById(order);

        outboundOrderLineMapper.delete(Wrappers.<OutboundOrderLine>lambdaQuery()
                .eq(OutboundOrderLine::getOutboundOrderId, order.getId()));
        insertLines(order.getId(), request.lines());
    }

    private void validateRequest(OutboundOrderRequest request) {
        for (OutboundOrderRequest.LineItem line : request.lines()) {
            Supplier supplier = requireEnabledSupplier(line.supplierId());
            Material material = requireEnabledMaterial(line.materialId());
            if (material.getSupplierId() != null && !Objects.equals(material.getSupplierId(), supplier.getId())) {
                throw new BusinessException("物料不属于所选供应商");
            }
            if (line.containerTypeId() != null) {
                requireEnabledContainerType(line.containerTypeId());
            }
        }
    }

    private Supplier requireEnabledSupplier(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null || !ENABLED.equals(supplier.getStatus())) {
            throw new BusinessException("供应商不存在或已停用");
        }
        return supplier;
    }

    private Material requireEnabledMaterial(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null || !ENABLED.equals(material.getStatus())) {
            throw new BusinessException("物料不存在或已停用");
        }
        return material;
    }

    private ContainerType requireEnabledContainerType(Long id) {
        ContainerType containerType = containerTypeMapper.selectById(id);
        if (containerType == null || !ENABLED.equals(containerType.getStatus())) {
            throw new BusinessException("容器类型不存在或已停用");
        }
        return containerType;
    }

    private void insertLines(Long orderId, List<OutboundOrderRequest.LineItem> requestLines) {
        int lineNo = 1;
        for (OutboundOrderRequest.LineItem requestLine : requestLines) {
            OutboundOrderLine line = new OutboundOrderLine();
            line.setOutboundOrderId(orderId);
            line.setLineNo(lineNo++);
            line.setMaterialId(requestLine.materialId());
            line.setSupplierId(requestLine.supplierId());
            line.setPlannedQty(requestLine.plannedQty());
            line.setPickedQty(BigDecimal.ZERO);
            line.setTargetWarehouseId(requestLine.targetWarehouseId());
            line.setTargetLocationId(requestLine.targetLocationId());
            line.setContainerTypeId(requestLine.containerTypeId());
            outboundOrderLineMapper.insert(line);
        }
    }

    private OutboundOrder requireOrder(Long id) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "出库单不存在");
        }
        return order;
    }

    private OutboundOrder requireLockedOrder(Long id) {
        OutboundOrder order = outboundOrderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "出库单不存在");
        }
        return order;
    }

    private boolean hasPicked(Long orderId) {
        return linesOf(orderId).stream()
                .map(OutboundOrderLine::getPickedQty)
                .filter(Objects::nonNull)
                .anyMatch(qty -> qty.compareTo(BigDecimal.ZERO) > 0);
    }

    private List<OutboundOrderLine> linesOf(Long orderId) {
        return outboundOrderLineMapper.selectList(Wrappers.<OutboundOrderLine>lambdaQuery()
                .eq(OutboundOrderLine::getOutboundOrderId, orderId)
                .orderByAsc(OutboundOrderLine::getLineNo));
    }

    private OutboundOrderResponse toResponse(Long id) {
        return toResponse(requireOrder(id));
    }

    private OutboundOrderResponse toResponse(OutboundOrder order) {
        Supplier supplier = supplierMapper.selectById(order.getSupplierId());
        List<OutboundOrderLine> lines = linesOf(order.getId());
        BigDecimal plannedQty = sum(lines, OutboundOrderLine::getPlannedQty);
        BigDecimal pickedQty = sum(lines, OutboundOrderLine::getPickedQty);

        return new OutboundOrderResponse(
                order.getId(),
                order.getOutboundNo(),
                supplier == null ? null : new OutboundOrderResponse.SupplierInfo(
                        supplier.getId(),
                        supplier.getSupplierCode(),
                        supplier.getSupplierName()),
                order.getPurpose(),
                order.getSourceDocNo(),
                order.getStatus(),
                order.getRemark(),
                lines.size(),
                plannedQty,
                pickedQty,
                order.getReleasedAt(),
                order.getCompletedAt(),
                order.getCreatedAt(),
                lines.stream()
                        .map(this::toLineDisplay)
                        .toList()
        );
    }

    private OutboundOrderResponse.LineDisplay toLineDisplay(OutboundOrderLine line) {
        Material material = materialMapper.selectById(line.getMaterialId());
        Supplier supplier = line.getSupplierId() == null ? null : supplierMapper.selectById(line.getSupplierId());
        Warehouse warehouse = line.getTargetWarehouseId() == null ? null : warehouseMapper.selectById(line.getTargetWarehouseId());
        StorageLocation location = line.getTargetLocationId() == null ? null : storageLocationMapper.selectById(line.getTargetLocationId());
        ContainerType ct = line.getContainerTypeId() == null ? null : containerTypeMapper.selectById(line.getContainerTypeId());

        return new OutboundOrderResponse.LineDisplay(
                line.getId(),
                line.getLineNo(),
                line.getMaterialId(),
                material == null ? null : material.getMaterialCode(),
                material == null ? null : material.getMaterialName(),
                supplier == null ? null : new OutboundOrderResponse.SupplierInfo(
                        supplier.getId(),
                        supplier.getSupplierCode(),
                        supplier.getSupplierName()),
                line.getPlannedQty(),
                line.getPickedQty(),
                line.getTargetWarehouseId(),
                warehouse == null ? null : warehouse.getWarehouseName(),
                line.getTargetLocationId(),
                location == null ? null : location.getLocationName(),
                line.getContainerTypeId(),
                ct == null ? null : ct.getContainerName(),
                ct == null ? null : ct.getCapacityQty()
        );
    }

    private BigDecimal sum(List<OutboundOrderLine> lines, Function<OutboundOrderLine, BigDecimal> value) {
        return lines.stream()
                .map(value)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generateOutboundNo() {
        return "OUT-" + LocalDate.now().format(OUTBOUND_NO_DATE) + "-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    private static List<String> splitStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return List.of();
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
