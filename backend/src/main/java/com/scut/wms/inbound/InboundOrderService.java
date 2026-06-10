package com.scut.wms.inbound;

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
public class InboundOrderService {
    private static final String ENABLED = "ENABLED";
    private static final String DRAFT = "DRAFT";
    private static final String RELEASED = "RELEASED";
    private static final String PARTIAL_RECEIVED = "PARTIAL_RECEIVED";
    private static final String COMPLETED = "COMPLETED";
    private static final String CANCELLED = "CANCELLED";
    private static final String PRINTED = "PRINTED";
    private static final String RECEIVED = "RECEIVED";
    private static final DateTimeFormatter INBOUND_NO_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final KanbanBoardMapper kanbanBoardMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;

    public InboundOrderService(
            InboundOrderMapper inboundOrderMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            KanbanBoardMapper kanbanBoardMapper,
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper
    ) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.kanbanBoardMapper = kanbanBoardMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
    }

    public List<InboundOrderResponse> list(String status, String inboundNo, Long supplierId, String supplierKeyword) {
        Set<Long> supplierIds = supplierFilterIds(supplierKeyword);
        if (StringUtils.hasText(supplierKeyword) && supplierIds.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<InboundOrder> query = Wrappers.<InboundOrder>lambdaQuery()
                .eq(StringUtils.hasText(status), InboundOrder::getStatus, status)
                .like(StringUtils.hasText(inboundNo), InboundOrder::getInboundNo, inboundNo)
                .eq(supplierId != null, InboundOrder::getSupplierId, supplierId)
                .in(!supplierIds.isEmpty(), InboundOrder::getSupplierId, supplierIds)
                .orderByDesc(InboundOrder::getCreatedAt)
                .orderByDesc(InboundOrder::getId);
        return inboundOrderMapper.selectList(query).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InboundOrderResponse create(InboundOrderRequest request) {
        validateRequest(request);

        InboundOrder order = new InboundOrder();
        order.setInboundNo(generateInboundNo());
        order.setSupplierId(request.supplierId());
        order.setSourceDocNo(request.sourceDocNo());
        order.setStatus(DRAFT);
        order.setRemark(request.remark());
        inboundOrderMapper.insert(order);

        insertLines(order.getId(), request.lines());
        return toResponse(order.getId());
    }

    @Transactional
    public InboundOrderResponse update(Long id, InboundOrderRequest request) {
        validateRequest(request);
        InboundOrder order = requireOrder(id);
        if (DRAFT.equals(order.getStatus())) {
            replaceOrder(order, request, false);
            return toResponse(id);
        }
        if (RELEASED.equals(order.getStatus())) {
            if (hasReceived(id)) {
                throw new BusinessException("已有收货记录的入库单不能修改");
            }
            replaceOrder(order, request, true);
            return toResponse(id);
        }
        throw new BusinessException("当前状态不允许修改入库单");
    }

    @Transactional
    public InboundOrderResponse release(Long id) {
        InboundOrder order = requireLockedOrder(id);
        if (RELEASED.equals(order.getStatus())) {
            return toResponse(id);
        }
        if (!DRAFT.equals(order.getStatus())) {
            throw new BusinessException("当前状态不允许释放入库单");
        }

        List<InboundOrderLine> lines = linesOf(id);
        if (lines.isEmpty()) {
            throw new BusinessException("入库单明细不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        List<KanbanBoard> existing = kanbansOf(id);
        if (existing.isEmpty()) {
            insertKanbans(order, lines, now);
        }

        order.setStatus(RELEASED);
        order.setReleasedAt(now);
        inboundOrderMapper.updateById(order);
        return toResponse(id);
    }

    @Transactional
    public InboundOrderResponse cancel(Long id) {
        InboundOrder order = requireOrder(id);
        if (CANCELLED.equals(order.getStatus())) {
            return toResponse(id);
        }
        if (COMPLETED.equals(order.getStatus()) || PARTIAL_RECEIVED.equals(order.getStatus()) || hasReceived(id)) {
            throw new BusinessException("已有收货记录的入库单不能取消");
        }

        order.setStatus(CANCELLED);
        inboundOrderMapper.updateById(order);

        for (KanbanBoard kanban : kanbansOf(id)) {
            kanban.setStatus(CANCELLED);
            kanbanBoardMapper.updateById(kanban);
        }
        return toResponse(id);
    }

    public InboundPrintResponse print(Long id) {
        requireOrder(id);
        InboundPrintHeader header = inboundOrderMapper.selectPrintHeader(id);
        List<InboundPrintLine> lines = inboundOrderMapper.selectPrintLines(id);
        return new InboundPrintResponse(
                header.id(),
                header.inboundNo(),
                header.supplierCode(),
                header.supplierName(),
                header.sourceDocNo(),
                header.status(),
                header.remark(),
                header.releasedAt(),
                lines
        );
    }

    public List<KanbanPrintResponse> printKanbans(Long id) {
        requireOrder(id);
        return inboundOrderMapper.selectKanbanPrints(id);
    }

    private void replaceOrder(InboundOrder order, InboundOrderRequest request, boolean rebuildKanbans) {
        order.setSupplierId(request.supplierId());
        order.setSourceDocNo(request.sourceDocNo());
        order.setRemark(request.remark());
        inboundOrderMapper.updateById(order);

        if (rebuildKanbans) {
            kanbanBoardMapper.delete(Wrappers.<KanbanBoard>lambdaQuery()
                    .eq(KanbanBoard::getInboundOrderId, order.getId()));
        }
        inboundOrderLineMapper.delete(Wrappers.<InboundOrderLine>lambdaQuery()
                .eq(InboundOrderLine::getInboundOrderId, order.getId()));
        insertLines(order.getId(), request.lines());

        if (rebuildKanbans) {
            insertKanbans(order, linesOf(order.getId()), LocalDateTime.now());
        }
    }

    private void validateRequest(InboundOrderRequest request) {
        requireEnabledSupplier(request.supplierId());
        for (InboundOrderRequest.Line line : request.lines()) {
            requireEnabledMaterial(line.materialId());
            Warehouse warehouse = requireEnabledWarehouse(line.targetWarehouseId());
            StorageLocation location = requireEnabledLocation(line.targetLocationId());
            if (!Objects.equals(location.getWarehouseId(), warehouse.getId())) {
                throw new BusinessException("目标库位不属于目标仓库");
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

    private void insertLines(Long orderId, List<InboundOrderRequest.Line> requestLines) {
        int lineNo = 1;
        for (InboundOrderRequest.Line requestLine : requestLines) {
            InboundOrderLine line = new InboundOrderLine();
            line.setInboundOrderId(orderId);
            line.setLineNo(lineNo++);
            line.setMaterialId(requestLine.materialId());
            line.setPlannedQty(requestLine.plannedQty());
            line.setReceivedQty(BigDecimal.ZERO);
            line.setTargetWarehouseId(requestLine.targetWarehouseId());
            line.setTargetLocationId(requestLine.targetLocationId());
            inboundOrderLineMapper.insert(line);
        }
    }

    private InboundOrder requireOrder(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入库单不存在");
        }
        return order;
    }

    private InboundOrder requireLockedOrder(Long id) {
        InboundOrder order = inboundOrderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "入库单不存在");
        }
        return order;
    }

    private void insertKanbans(InboundOrder order, List<InboundOrderLine> lines, LocalDateTime printedAt) {
        for (InboundOrderLine line : lines) {
            KanbanBoard board = new KanbanBoard();
            board.setKanbanCode("KB:v1:%s:%d:%d".formatted(order.getInboundNo(), line.getLineNo(), 1));
            board.setInboundOrderId(order.getId());
            board.setInboundOrderLineId(line.getId());
            board.setBoardQty(line.getPlannedQty());
            board.setStatus(PRINTED);
            board.setPrintedAt(printedAt);
            kanbanBoardMapper.insert(board);
        }
    }

    private boolean hasReceived(Long orderId) {
        boolean hasReceivedLine = linesOf(orderId).stream()
                .map(InboundOrderLine::getReceivedQty)
                .filter(Objects::nonNull)
                .anyMatch(qty -> qty.compareTo(BigDecimal.ZERO) > 0);
        if (hasReceivedLine) {
            return true;
        }
        return kanbansOf(orderId).stream()
                .anyMatch(kanban -> kanban.getReceivedAt() != null || RECEIVED.equals(kanban.getStatus()));
    }

    private List<InboundOrderLine> linesOf(Long orderId) {
        return inboundOrderLineMapper.selectList(Wrappers.<InboundOrderLine>lambdaQuery()
                .eq(InboundOrderLine::getInboundOrderId, orderId)
                .orderByAsc(InboundOrderLine::getLineNo));
    }

    private List<KanbanBoard> kanbansOf(Long orderId) {
        return kanbanBoardMapper.selectList(Wrappers.<KanbanBoard>lambdaQuery()
                .eq(KanbanBoard::getInboundOrderId, orderId)
                .orderByAsc(KanbanBoard::getId));
    }

    private InboundOrderResponse toResponse(Long id) {
        return toResponse(requireOrder(id));
    }

    private InboundOrderResponse toResponse(InboundOrder order) {
        Supplier supplier = supplierMapper.selectById(order.getSupplierId());
        List<InboundOrderLine> lines = linesOf(order.getId());
        BigDecimal plannedQty = sum(lines, InboundOrderLine::getPlannedQty);
        BigDecimal receivedQty = sum(lines, InboundOrderLine::getReceivedQty);

        return new InboundOrderResponse(
                order.getId(),
                order.getInboundNo(),
                supplier == null ? null : new InboundOrderResponse.SupplierDisplay(
                        supplier.getId(),
                        supplier.getSupplierCode(),
                        supplier.getSupplierName()),
                order.getSourceDocNo(),
                order.getStatus(),
                order.getRemark(),
                order.getCreatedAt(),
                order.getReleasedAt(),
                order.getCompletedAt(),
                lines.size(),
                plannedQty,
                receivedQty,
                lines.stream()
                        .map(line -> new InboundOrderResponse.LineDisplay(
                                line.getId(),
                                line.getLineNo(),
                                line.getMaterialId(),
                                line.getPlannedQty(),
                                line.getReceivedQty(),
                                line.getTargetWarehouseId(),
                                line.getTargetLocationId()))
                        .toList()
        );
    }

    private BigDecimal sum(List<InboundOrderLine> lines, Function<InboundOrderLine, BigDecimal> value) {
        return lines.stream()
                .map(value)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Set<Long> supplierFilterIds(String supplierKeyword) {
        if (!StringUtils.hasText(supplierKeyword)) {
            return Collections.emptySet();
        }
        return supplierMapper.selectList(Wrappers.<Supplier>lambdaQuery()
                        .like(Supplier::getSupplierCode, supplierKeyword)
                        .or()
                        .like(Supplier::getSupplierName, supplierKeyword))
                .stream()
                .map(Supplier::getId)
                .collect(Collectors.toSet());
    }

    private String generateInboundNo() {
        return "IN-" + LocalDate.now().format(INBOUND_NO_DATE) + "-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }
}
