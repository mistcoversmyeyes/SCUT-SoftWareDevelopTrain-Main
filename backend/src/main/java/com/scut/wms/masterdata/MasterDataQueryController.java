package com.scut.wms.masterdata;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
public class MasterDataQueryController {
    private static final String ENABLED = "ENABLED";

    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;

    public MasterDataQueryController(
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

    @GetMapping("/api/suppliers")
    public List<Supplier> listSuppliers(@RequestParam(required = false) String name) {
        if (name != null && !name.isBlank()) {
            return supplierMapper.selectList(Wrappers.<Supplier>lambdaQuery()
                    .like(Supplier::getSupplierName, name)
                    .orderByAsc(Supplier::getSupplierCode));
        }
        return supplierMapper.selectList(Wrappers.<Supplier>lambdaQuery()
                .orderByAsc(Supplier::getSupplierCode));
    }

    @GetMapping("/api/materials")
    public List<Material> listMaterials(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String materialName,
            @RequestParam(required = false) Long supplierId
    ) {
        var query = Wrappers.<Material>lambdaQuery().orderByAsc(Material::getMaterialCode);
        if (materialCode != null && !materialCode.isBlank()) {
            query.like(Material::getMaterialCode, materialCode);
        }
        if (materialName != null && !materialName.isBlank()) {
            query.like(Material::getMaterialName, materialName);
        }
        if (supplierId != null) {
            query.eq(Material::getSupplierId, supplierId);
        }
        List<Material> materials = materialMapper.selectList(query);

        // Populate supplier info (batch load to avoid N+1)
        Set<Long> supplierIds = materials.stream()
                .map(Material::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!supplierIds.isEmpty()) {
            Map<Long, Supplier> supplierMap = supplierMapper.selectBatchIds(supplierIds).stream()
                    .collect(Collectors.toMap(Supplier::getId, s -> s));
            for (Material m : materials) {
                m.setSupplier(supplierMap.get(m.getSupplierId()));
            }
        }

        return materials;
    }

    @GetMapping("/api/warehouses")
    public List<Warehouse> listWarehouses() {
        return warehouseMapper.selectList(Wrappers.<Warehouse>lambdaQuery()
                .orderByAsc(Warehouse::getWarehouseCode));
    }

    @GetMapping("/api/storage-locations")
    public List<StorageLocation> listStorageLocations(@RequestParam(required = false) Long warehouseId) {
        var query = Wrappers.<StorageLocation>lambdaQuery()
                .orderByAsc(StorageLocation::getWarehouseId)
                .orderByAsc(StorageLocation::getLocationCode);
        if (warehouseId != null) {
            query.eq(StorageLocation::getWarehouseId, warehouseId);
        }
        return storageLocationMapper.selectList(query);
    }
}
