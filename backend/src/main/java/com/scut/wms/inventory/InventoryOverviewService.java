package com.scut.wms.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scut.wms.inbound.InventoryTag;
import com.scut.wms.inbound.InventoryTagMapper;
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

    public InventoryOverviewService(
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper,
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTagMapper inventoryTagMapper
    ) {
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTagMapper = inventoryTagMapper;
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

                stocks.add(new InventoryOverviewResponse.MaterialStock(
                        mat.getId(),
                        mat.getMaterialCode(),
                        mat.getMaterialName(),
                        mat.getHighStockQty(),
                        current
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
}
