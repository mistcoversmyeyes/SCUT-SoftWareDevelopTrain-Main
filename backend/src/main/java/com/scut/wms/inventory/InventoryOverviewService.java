package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.inbound.InboundOrderLine;
import com.scut.wms.inbound.InboundOrderLineMapper;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
import com.scut.wms.lock.InventoryHold;
import com.scut.wms.lock.InventoryHoldMapper;
import com.scut.wms.masterdata.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class InventoryOverviewService {

    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTagMapper inventoryTagMapper;
    private final InboundOrderLineMapper inboundOrderLineMapper;
    private final InventoryHoldMapper inventoryHoldMapper;

    public InventoryOverviewService(
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper,
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTagMapper inventoryTagMapper,
            InboundOrderLineMapper inboundOrderLineMapper,
            InventoryHoldMapper inventoryHoldMapper
    ) {
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTagMapper = inventoryTagMapper;
        this.inboundOrderLineMapper = inboundOrderLineMapper;
        this.inventoryHoldMapper = inventoryHoldMapper;
    }

    public InventoryOverviewResponse overview() {
        return new InventoryOverviewResponse(
                buildWarehouseOverviews(),
                buildSupplierOverviews()
        );
    }

    private List<InventoryOverviewResponse.WarehouseOverview> buildWarehouseOverviews() {
        // Load all RECEIVED/LOCKED inventoryTags once, group by location_id
        List<InventoryTag> activeInventoryTags = inventoryTagMapper.selectList(
                new LambdaQueryWrapper<InventoryTag>()
                        .in(InventoryTag::getStatus, "RECEIVED", "LOCKED")
                        .gt(InventoryTag::getLocationId, 0L)
        );

        Map<Long, Integer> boxCount = new HashMap<>();
        Map<Long, BigDecimal> pieceSum = new HashMap<>();
        for (InventoryTag kb : activeInventoryTags) {
            Long locId = kb.getLocationId();
            boxCount.merge(locId, 1, Integer::sum);
            BigDecimal boardQty = kb.getBoardQty() != null ? kb.getBoardQty() : BigDecimal.ZERO;
            BigDecimal pickedQty = kb.getPickedQty() != null ? kb.getPickedQty() : BigDecimal.ZERO;
            BigDecimal remaining = boardQty.subtract(pickedQty);
            pieceSum.merge(locId, remaining.compareTo(BigDecimal.ZERO) > 0 ? remaining : BigDecimal.ZERO, BigDecimal::add);
        }

        List<InventoryOverviewResponse.WarehouseOverview> result = new ArrayList<>();
        List<Warehouse> warehouses = warehouseMapper.selectList(null);

        for (Warehouse wh : warehouses) {
            List<StorageLocation> locations = storageLocationMapper.selectList(
                    new LambdaQueryWrapper<StorageLocation>()
                            .eq(StorageLocation::getWarehouseId, wh.getId())
            );

            List<InventoryOverviewResponse.LocationSlot> slots = new ArrayList<>();
            for (StorageLocation loc : locations) {
                int usedBoxes = boxCount.getOrDefault(loc.getId(), 0);
                BigDecimal totalPieces = pieceSum.getOrDefault(loc.getId(), BigDecimal.ZERO);

                slots.add(new InventoryOverviewResponse.LocationSlot(
                        loc.getId(),
                        loc.getWarehouseId(),
                        loc.getLocationCode(),
                        loc.getLocationName(),
                        loc.getMaxCapacity(),
                        usedBoxes,
                        totalPieces
                ));
            }

            result.add(new InventoryOverviewResponse.WarehouseOverview(
                    wh.getId(),
                    wh.getWarehouseCode(),
                    wh.getWarehouseName(),
                    slots
            ));
        }

        return result;
    }

    private List<InventoryOverviewResponse.SupplierMaterialOverview> buildSupplierOverviews() {
        List<InventoryOverviewResponse.SupplierMaterialOverview> result = new ArrayList<>();
        List<Supplier> suppliers = supplierMapper.selectList(null);
        List<Material> materials = materialMapper.selectList(null);
        List<InventoryBalance> balances = inventoryBalanceMapper.selectList(null);
        Map<Long, BigDecimal> availableByMaterialId = buildAvailableQtyByMaterialId();

        for (Supplier sup : suppliers) {
            List<Material> supMaterials = materials.stream()
                    .filter(m -> Objects.equals(m.getSupplierId(), sup.getId())
                            && "ENABLED".equals(m.getStatus()))
                    .toList();

            if (supMaterials.isEmpty()) continue;

            List<InventoryOverviewResponse.MaterialStock> stocks = new ArrayList<>();
            for (Material mat : supMaterials) {
                BigDecimal current = balances.stream()
                        .filter(b -> Objects.equals(b.getMaterialId(), mat.getId()))
                        .map(InventoryBalance::getOnHandQty)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal available = availableByMaterialId.getOrDefault(mat.getId(), BigDecimal.ZERO);
                BigDecimal low = mat.getLowStockQty();
                boolean shortage = low != null
                        && low.compareTo(BigDecimal.ZERO) > 0
                        && available.compareTo(low) <= 0;

                stocks.add(new InventoryOverviewResponse.MaterialStock(
                        mat.getId(),
                        mat.getMaterialCode(),
                        mat.getMaterialName(),
                        low,
                        mat.getHighStockQty(),
                        current,
                        available,
                        shortage
                ));
            }

            result.add(new InventoryOverviewResponse.SupplierMaterialOverview(
                    sup.getId(),
                    sup.getSupplierCode(),
                    sup.getSupplierName(),
                    stocks
            ));
        }

        return result;
    }

    private Map<Long, BigDecimal> buildAvailableQtyByMaterialId() {
        List<InventoryTag> receivedTags = inventoryTagMapper.selectList(
                Wrappers.<InventoryTag>lambdaQuery()
                        .eq(InventoryTag::getStatus, "RECEIVED")
        );
        if (receivedTags.isEmpty()) {
            return Map.of();
        }

        Set<Long> activeHoldTagIds = inventoryHoldMapper.selectList(
                        Wrappers.<InventoryHold>lambdaQuery()
                                .eq(InventoryHold::getStatus, InventoryHold.ACTIVE)
                ).stream()
                .map(InventoryHold::getInventoryTagId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        Map<Long, Long> materialIdByLineId = inboundOrderLineMapper.selectList(null).stream()
                .filter(line -> line.getId() != null && line.getMaterialId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        InboundOrderLine::getId,
                        InboundOrderLine::getMaterialId,
                        (left, right) -> left
                ));

        Map<Long, BigDecimal> availableByMaterialId = new HashMap<>();
        for (InventoryTag tag : receivedTags) {
            if (tag.getId() != null && activeHoldTagIds.contains(tag.getId())) {
                continue;
            }
            Long materialId = materialIdByLineId.get(tag.getInboundOrderLineId());
            if (materialId == null) {
                continue;
            }
            BigDecimal boardQty = tag.getBoardQty() != null ? tag.getBoardQty() : BigDecimal.ZERO;
            BigDecimal pickedQty = tag.getPickedQty() != null ? tag.getPickedQty() : BigDecimal.ZERO;
            BigDecimal remaining = boardQty.subtract(pickedQty);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            availableByMaterialId.merge(materialId, remaining, BigDecimal::add);
        }
        return availableByMaterialId;
    }
}
