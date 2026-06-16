package com.scut.wms.masterdata;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MasterDataController {
    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    @GetMapping("/master-data/options")
    public MasterDataOptionsResponse options() {
        return service.options();
    }

    @PostMapping("/suppliers")
    public Supplier createSupplier(@RequestBody SupplierRequest request) {
        return service.createSupplier(request);
    }

    @PutMapping("/suppliers/{id}")
    public Supplier updateSupplier(@PathVariable Long id, @RequestBody SupplierRequest request) {
        return service.updateSupplier(id, request);
    }

    @PutMapping("/suppliers/{id}/status")
    public void updateSupplierStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateSupplierStatus(id, body.get("status"));
    }

    @PostMapping("/materials")
    public Material createMaterial(@RequestBody MaterialRequest request) {
        return service.createMaterial(request);
    }

    @PutMapping("/materials/{id}")
    public Material updateMaterial(@PathVariable Long id, @RequestBody MaterialRequest request) {
        return service.updateMaterial(id, request);
    }

    @PutMapping("/materials/{id}/status")
    public void updateMaterialStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateMaterialStatus(id, body.get("status"));
    }

    @GetMapping("/materials/{id}/container-types")
    public List<MaterialContainerTypeResponse> getMaterialContainerTypes(@PathVariable Long id) {
        return service.getMaterialContainerTypes(id);
    }

    @PutMapping("/materials/{id}/container-types")
    public void updateMaterialContainerTypes(@PathVariable Long id, @RequestBody MaterialContainerTypeUpdateRequest request) {
        service.updateMaterialContainerTypes(id, request);
    }

    @PostMapping("/warehouses")
    public Warehouse createWarehouse(@RequestBody WarehouseRequest request) {
        return service.createWarehouse(request);
    }

    @PutMapping("/warehouses/{id}")
    public Warehouse updateWarehouse(@PathVariable Long id, @RequestBody WarehouseRequest request) {
        return service.updateWarehouse(id, request);
    }

    @PutMapping("/warehouses/{id}/status")
    public void updateWarehouseStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateWarehouseStatus(id, body.get("status"));
    }

    @PostMapping("/storage-locations")
    public StorageLocation createStorageLocation(@RequestBody StorageLocationRequest request) {
        return service.createStorageLocation(request);
    }

    @PutMapping("/storage-locations/{id}")
    public StorageLocation updateStorageLocation(@PathVariable Long id, @RequestBody StorageLocationRequest request) {
        return service.updateStorageLocation(id, request);
    }

    @PutMapping("/storage-locations/{id}/status")
    public void updateStorageLocationStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        service.updateStorageLocationStatus(id, body.get("status"));
    }
}
