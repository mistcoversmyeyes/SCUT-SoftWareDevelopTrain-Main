package com.scut.wms.outbound.picking;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.inventory.InventoryTransactionMapper;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.masterdata.Warehouse;
import com.scut.wms.masterdata.WarehouseMapper;
import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderLine;
import com.scut.wms.outbound.OutboundOrderLineMapper;
import com.scut.wms.outbound.OutboundOrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OutboundRecommendationService {
    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderLineMapper lineMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final InventoryTransactionMapper transactionMapper;

    public OutboundRecommendationService(
            OutboundOrderMapper orderMapper,
            OutboundOrderLineMapper lineMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            InventoryTransactionMapper transactionMapper
    ) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.transactionMapper = transactionMapper;
    }

    public OutboundRecommendationResponse recommend(Long outboundOrderId) {
        OutboundOrder order = orderMapper.selectById(outboundOrderId);
        if (order == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "出库单不存在");
        }
        List<OutboundOrderLine> lines = lineMapper.selectList(
                Wrappers.<OutboundOrderLine>lambdaQuery()
                        .eq(OutboundOrderLine::getOutboundOrderId, outboundOrderId)
                        .orderByAsc(OutboundOrderLine::getLineNo)
        );
        return new OutboundRecommendationResponse(
                order.getId(),
                order.getOutboundNo(),
                lines.stream().map(this::recommendLine).toList()
        );
    }

    public boolean containsRecommendedTag(Long outboundOrderLineId, String inventoryTagCode) {
        OutboundOrderLine line = lineMapper.selectById(outboundOrderLineId);
        if (line == null) {
            throw new BusinessException("出库单明细行不存在");
        }
        return recommendationsFor(line).stream()
                .anyMatch(item -> item.inventoryTagCode().equals(inventoryTagCode));
    }

    private OutboundRecommendationLine recommendLine(OutboundOrderLine line) {
        Material material = materialMapper.selectById(line.getMaterialId());
        BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
        BigDecimal needed = line.getPlannedQty().subtract(picked);
        return new OutboundRecommendationLine(
                line.getId(),
                line.getLineNo(),
                line.getMaterialId(),
                material == null ? null : material.getMaterialCode(),
                material == null ? null : material.getMaterialName(),
                needed,
                recommendationsFor(line)
        );
    }

    private List<PickRecommendation> recommendationsFor(OutboundOrderLine line) {
        List<Long> warehouseIds = recommendationWarehouseIds(line);
        if (warehouseIds.isEmpty()) {
            return List.of();
        }
        List<PickRecommendation> candidates = transactionMapper.selectFifoRecommendations(
                line.getMaterialId(),
                warehouseIds
        );
        BigDecimal picked = line.getPickedQty() == null ? BigDecimal.ZERO : line.getPickedQty();
        BigDecimal needed = line.getPlannedQty().subtract(picked);
        if (needed.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }

        BigDecimal recommendedQty = BigDecimal.ZERO;
        ArrayList<PickRecommendation> recommendations = new ArrayList<>();
        for (PickRecommendation candidate : candidates) {
            if (recommendedQty.compareTo(needed) >= 0) {
                break;
            }
            recommendations.add(candidate);
            recommendedQty = recommendedQty.add(candidate.availableQty());
        }
        return recommendations;
    }

    private List<Long> recommendationWarehouseIds(OutboundOrderLine line) {
        if (line.getTargetWarehouseId() != null) {
            return List.of(line.getTargetWarehouseId());
        }
        return warehouseMapper.selectList(Wrappers.<Warehouse>lambdaQuery()
                        .orderByAsc(Warehouse::getId))
                .stream()
                .map(Warehouse::getId)
                .toList();
    }
}
