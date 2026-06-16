package com.scut.wms.masterdata;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.common.BusinessException;
import com.scut.wms.container.ContainerType;
import com.scut.wms.container.ContainerTypeMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MasterDataService {
    private static final String ENABLED = "ENABLED";

    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageLocationMapper storageLocationMapper;
    private final ContainerTypeMapper containerTypeMapper;

    public MasterDataService(
            SupplierMapper supplierMapper,
            MaterialMapper materialMapper,
            WarehouseMapper warehouseMapper,
            StorageLocationMapper storageLocationMapper,
            ContainerTypeMapper containerTypeMapper
    ) {
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.warehouseMapper = warehouseMapper;
        this.storageLocationMapper = storageLocationMapper;
        this.containerTypeMapper = containerTypeMapper;
    }

    public MasterDataOptionsResponse options() {
        return new MasterDataOptionsResponse(
                supplierOptions(),
                materialOptions(),
                warehouseOptions(),
                locationOptions(),
                containerTypeOptions()
        );
    }

    @Transactional
    public Supplier createSupplier(SupplierRequest request) {
        Supplier existing = supplierMapper.selectOne(Wrappers.<Supplier>lambdaQuery()
                .eq(Supplier::getSupplierCode, request.supplierCode()));
        if (existing != null) {
            throw new BusinessException("供应商编码已存在");
        }
        Supplier supplier = new Supplier();
        supplier.setSupplierCode(request.supplierCode());
        supplier.setSupplierName(request.supplierName());
        supplier.setContactName(request.contactName());
        supplier.setContactPhone(request.contactPhone());
        supplier.setStatus(ENABLED);
        supplierMapper.insert(supplier);
        return supplier;
    }

    @Transactional
    public Supplier updateSupplier(Long id, SupplierRequest request) {
        Supplier supplier = requireSupplier(id);
        Supplier existing = supplierMapper.selectOne(Wrappers.<Supplier>lambdaQuery()
                .eq(Supplier::getSupplierCode, request.supplierCode())
                .ne(Supplier::getId, id));
        if (existing != null) {
            throw new BusinessException("供应商编码已存在");
        }
        supplier.setSupplierCode(request.supplierCode());
        supplier.setSupplierName(request.supplierName());
        supplier.setContactName(request.contactName());
        supplier.setContactPhone(request.contactPhone());
        supplierMapper.updateById(supplier);
        return supplier;
    }

    @Transactional
    public void updateSupplierStatus(Long id, String status) {
        Supplier supplier = requireSupplier(id);
        supplier.setStatus(status);
        supplierMapper.updateById(supplier);
    }

    @Transactional
    public Material createMaterial(MaterialRequest request) {
        Material existing = materialMapper.selectOne(Wrappers.<Material>lambdaQuery()
                .eq(Material::getMaterialCode, request.materialCode()));
        if (existing != null) {
            throw new BusinessException("物料编码已存在");
        }
        validateMaterialReferences(request);
        Material material = new Material();
        material.setMaterialCode(request.materialCode());
        material.setMaterialName(request.materialName());
        material.setSpecification(request.specification());
        material.setUnit(request.unit());
        material.setSupplierId(request.supplierId());
        // containerTypeId removed — replaced by material_container_type middle table (D02)
        material.setLowStockQty(request.lowStockQty());
        material.setHighStockQty(request.highStockQty());
        material.setStatus(ENABLED);
        materialMapper.insert(material);
        return material;
    }

    @Transactional
    public Material updateMaterial(Long id, MaterialRequest request) {
        Material material = requireMaterial(id);
        Material existing = materialMapper.selectOne(Wrappers.<Material>lambdaQuery()
                .eq(Material::getMaterialCode, request.materialCode())
                .ne(Material::getId, id));
        if (existing != null) {
            throw new BusinessException("物料编码已存在");
        }
        validateMaterialReferences(request);
        material.setMaterialCode(request.materialCode());
        material.setMaterialName(request.materialName());
        material.setSpecification(request.specification());
        material.setUnit(request.unit());
        material.setSupplierId(request.supplierId());
        // containerTypeId removed — replaced by material_container_type middle table (D02)
        material.setLowStockQty(request.lowStockQty());
        material.setHighStockQty(request.highStockQty());
        materialMapper.updateById(material);
        return material;
    }

    @Transactional
    public void updateMaterialStatus(Long id, String status) {
        Material material = requireMaterial(id);
        material.setStatus(status);
        materialMapper.updateById(material);
    }

    @Transactional
    public Warehouse createWarehouse(WarehouseRequest request) {
        Warehouse existing = warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery()
                .eq(Warehouse::getWarehouseCode, request.warehouseCode()));
        if (existing != null) {
            throw new BusinessException("仓库编码已存在");
        }
        Warehouse warehouse = new Warehouse();
        warehouse.setWarehouseCode(request.warehouseCode());
        warehouse.setWarehouseName(request.warehouseName());
        warehouse.setStatus(request.status() != null ? request.status() : ENABLED);
        warehouseMapper.insert(warehouse);
        return warehouse;
    }

    @Transactional
    public Warehouse updateWarehouse(Long id, WarehouseRequest request) {
        Warehouse warehouse = requireWarehouse(id);
        Warehouse existing = warehouseMapper.selectOne(Wrappers.<Warehouse>lambdaQuery()
                .eq(Warehouse::getWarehouseCode, request.warehouseCode())
                .ne(Warehouse::getId, id));
        if (existing != null) {
            throw new BusinessException("仓库编码已存在");
        }
        warehouse.setWarehouseCode(request.warehouseCode());
        warehouse.setWarehouseName(request.warehouseName());
        if (request.status() != null) {
            warehouse.setStatus(request.status());
        }
        warehouseMapper.updateById(warehouse);
        return warehouse;
    }

    @Transactional
    public void updateWarehouseStatus(Long id, String status) {
        Warehouse warehouse = requireWarehouse(id);
        warehouse.setStatus(status);
        warehouseMapper.updateById(warehouse);
    }
    @Transactional
    public StorageLocation createStorageLocation(StorageLocationRequest request) {
        StorageLocation existing = storageLocationMapper.selectOne(Wrappers.<StorageLocation>lambdaQuery()
                .eq(StorageLocation::getLocationCode, request.locationCode()));
        if (existing != null) {
            throw new BusinessException("库位编码已存在");
        }
        Warehouse warehouse = warehouseMapper.selectById(request.warehouseId());
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }
        StorageLocation location = new StorageLocation();
        location.setWarehouseId(request.warehouseId());
        location.setLocationCode(request.locationCode());
        location.setLocationName(request.locationName());
        location.setStatus(request.status() != null ? request.status() : ENABLED);
        storageLocationMapper.insert(location);
        return location;
    }

    @Transactional
    public StorageLocation updateStorageLocation(Long id, StorageLocationRequest request) {
        StorageLocation location = requireStorageLocation(id);
        StorageLocation existing = storageLocationMapper.selectOne(Wrappers.<StorageLocation>lambdaQuery()
                .eq(StorageLocation::getLocationCode, request.locationCode())
                .ne(StorageLocation::getId, id));
        if (existing != null) {
            throw new BusinessException("库位编码已存在");
        }
        Warehouse warehouse = warehouseMapper.selectById(request.warehouseId());
        if (warehouse == null) {
            throw new BusinessException("仓库不存在");
        }
        location.setWarehouseId(request.warehouseId());
        location.setLocationCode(request.locationCode());
        location.setLocationName(request.locationName());
        if (request.status() != null) {
            location.setStatus(request.status());
        }
        storageLocationMapper.updateById(location);
        return location;
    }

    @Transactional
    public void updateStorageLocationStatus(Long id, String status) {
        StorageLocation location = requireStorageLocation(id);
        location.setStatus(status);
        storageLocationMapper.updateById(location);
    }

    private void validateMaterialReferences(MaterialRequest request) {
        if (request.supplierId() != null) {
            Supplier supplier = supplierMapper.selectById(request.supplierId());
            if (supplier == null) {
                throw new BusinessException("供应商不存在");
            }
        }
        // containerTypeId validation removed — D02: container association moved to
        // separate PUT /api/materials/{id}/container-types endpoint
    }

    private Supplier requireSupplier(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "供应商不存在");
        }
        return supplier;
    }

    private Material requireMaterial(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "物料不存在");
        }
        return material;
    }

    private Warehouse requireWarehouse(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "仓库不存在");
        }
        return warehouse;
    }

    private StorageLocation requireStorageLocation(Long id) {
        StorageLocation location = storageLocationMapper.selectById(id);
        if (location == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "库位不存在");
        }
        return location;
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

    private List<OptionItem> containerTypeOptions() {
        return containerTypeMapper.selectList(Wrappers.<ContainerType>lambdaQuery()
                        .eq(ContainerType::getStatus, ENABLED)
                        .orderByAsc(ContainerType::getId))
                .stream()
                .map(type -> new OptionItem(
                        type.getId(),
                        type.getContainerCode(),
                        type.getContainerName()))
                .toList();
    }
}
