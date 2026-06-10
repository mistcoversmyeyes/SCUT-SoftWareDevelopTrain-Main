package com.scut.wms.masterdata;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MasterDataService {
    private static final String ENABLED = "ENABLED";

    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;

    public MasterDataService(
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper
    ) {
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
    }

    public MasterDataOptionsResponse options() {
        return new MasterDataOptionsResponse(
                supplierOptions(),
                materialOptions(),
                warehouseOptions(),
                locationOptions()
        );
    }

    private List<OptionItem> supplierOptions() {
        return supplierMapper.selectList(Wrappers.<Supplier>lambdaQuery()
                        .eq(Supplier::getStatus, ENABLED)
                        .orderByAsc(Supplier::getId))
                .stream()
                .map(supplier -> new OptionItem(
                        supplier.getId(),
                        supplier.getSupplierCode(),
                        supplier.getSupplierName()))
                .toList();
    }

    private List<OptionItem> materialOptions() {
        return materialMapper.selectList(Wrappers.<Material>lambdaQuery()
                        .eq(Material::getStatus, ENABLED)
                        .orderByAsc(Material::getId))
                .stream()
                .map(material -> new OptionItem(
                        material.getId(),
                        material.getMaterialCode(),
                        material.getMaterialName()))
                .toList();
    }

    private List<OptionItem> warehouseOptions() {
        return warehouseMapper.selectList(Wrappers.<Warehouse>lambdaQuery()
                        .eq(Warehouse::getStatus, ENABLED)
                        .orderByAsc(Warehouse::getId))
                .stream()
                .map(warehouse -> new OptionItem(
                        warehouse.getId(),
                        warehouse.getWarehouseCode(),
                        warehouse.getWarehouseName()))
                .toList();
    }

    private List<LocationOption> locationOptions() {
        return storageLocationMapper.selectList(Wrappers.<StorageLocation>lambdaQuery()
                        .eq(StorageLocation::getStatus, ENABLED)
                        .orderByAsc(StorageLocation::getWarehouseId)
                        .orderByAsc(StorageLocation::getId))
                .stream()
                .map(location -> new LocationOption(
                        location.getId(),
                        location.getWarehouseId(),
                        location.getLocationCode(),
                        location.getLocationName()))
                .toList();
    }
}
