package com.scut.wms.outbound;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
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

    public OutboundOrderService(
            OutboundOrderMapper outboundOrderMapper,
            OutboundOrderLineMapper outboundOrderLineMapper,
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper
    ) {
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderLineMapper = outboundOrderLineMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
    }

    public List<OutboundOrderResponse> list(String status, String outboundNo, Long supplierId) {
        LambdaQueryWrapper<OutboundOrder> query = Wrappers.<OutboundOrder>lambdaQuery()
                .eq(StringUtils.hasText(status), OutboundOrder::getStatus, status)
                .like(StringUtils.hasText(outboundNo), OutboundOrder::getOutboundNo, outboundNo)
                .eq(supplierId != null, OutboundOrder::getSupplierId, supplierId)
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
        order.setSupplierId(request.supplierId());
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
        OutboundOrder order = requireOrder(id);
        if (CANCELLED.equals(order.getStatus())) {
            return toResponse(id);
        }
        if (COMPLETED.equals(order.getStatus()) || hasPicked(id)) {
            throw new BusinessException("已有拣货记录的出库单不能取消");
        }
        order.setStatus(CANCELLED);
        outboundOrderMapper.updateById(order);
        return toResponse(id);
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

    private void replaceOrder(OutboundOrder order, OutboundOrderRequest request) {
        order.setSupplierId(request.supplierId());
        order.setPurpose(request.purpose());
        order.setSourceDocNo(request.sourceDocNo());
        order.setRemark(request.remark());
        outboundOrderMapper.updateById(order);

        outboundOrderLineMapper.delete(Wrappers.<OutboundOrderLine>lambdaQuery()
                .eq(OutboundOrderLine::getOutboundOrderId, order.getId()));
        insertLines(order.getId(), request.lines());
    }

    private void validateRequest(OutboundOrderRequest request) {
        requireEnabledSupplier(request.supplierId());
        for (OutboundOrderRequest.LineItem line : request.lines()) {
            requireEnabledMaterial(line.materialId());
            Warehouse warehouse = requireEnabledWarehouse(line.sourceWarehouseId());
            StorageLocation location = requireEnabledLocation(line.sourceLocationId());
            if (!Objects.equals(location.getWarehouseId(), warehouse.getId())) {
                throw new BusinessException("来源库位不属于来源仓库");
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

    private Warehouse requireEnabledWarehouse(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || !ENABLED.equals(warehouse.getStatus())) {
            throw new BusinessException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private StorageLocation requireEnabledLocation(Long id) {
        StorageLocation location = storageLocationMapper.selectById(id);
        if (location == null || !ENABLED.equals(location.getStatus())) {
            throw new BusinessException("库位不存在或已停用");
        }
        return location;
    }

    private void insertLines(Long orderId, List<OutboundOrderRequest.LineItem> requestLines) {
        int lineNo = 1;
        for (OutboundOrderRequest.LineItem requestLine : requestLines) {
            OutboundOrderLine line = new OutboundOrderLine();
            line.setOutboundOrderId(orderId);
            line.setLineNo(lineNo++);
            line.setMaterialId(requestLine.materialId());
            line.setPlannedQty(requestLine.plannedQty());
            line.setPickedQty(BigDecimal.ZERO);
            line.setSourceWarehouseId(requestLine.sourceWarehouseId());
            line.setSourceLocationId(requestLine.sourceLocationId());
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
        Warehouse warehouse = warehouseMapper.selectById(line.getSourceWarehouseId());
        StorageLocation location = storageLocationMapper.selectById(line.getSourceLocationId());

        return new OutboundOrderResponse.LineDisplay(
                line.getId(),
                line.getLineNo(),
                line.getMaterialId(),
                material == null ? null : material.getMaterialCode(),
                material == null ? null : material.getMaterialName(),
                line.getPlannedQty(),
                line.getPickedQty(),
                line.getSourceWarehouseId(),
                warehouse == null ? null : warehouse.getWarehouseCode(),
                warehouse == null ? null : warehouse.getWarehouseName(),
                line.getSourceLocationId(),
                location == null ? null : location.getLocationCode(),
                location == null ? null : location.getLocationName()
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
}
