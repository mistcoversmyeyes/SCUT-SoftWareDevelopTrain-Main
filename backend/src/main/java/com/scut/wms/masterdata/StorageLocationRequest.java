package com.scut.wms.masterdata;

public record StorageLocationRequest(
        Long warehouseId,
        String locationCode,
        String locationName
) {
}
