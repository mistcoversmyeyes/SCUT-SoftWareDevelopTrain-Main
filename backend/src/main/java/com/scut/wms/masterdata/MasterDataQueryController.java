package com.scut.wms.masterdata;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
            @RequestParam(required = false) String materialName
    ) {
        var query = Wrappers.<Material>lambdaQuery().orderByAsc(Material::getMaterialCode);
        if (materialCode != null && !materialCode.isBlank()) {
            query.like(Material::getMaterialCode, materialCode);
        }
        if (materialName != null && !materialName.isBlank()) {
            query.like(Material::getMaterialName, materialName);
        }
        return materialMapper.selectList(query);
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
