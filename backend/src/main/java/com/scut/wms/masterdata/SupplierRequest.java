package com.scut.wms.masterdata;

public record SupplierRequest(
        String supplierCode,
        String supplierName,
        String contactName,
        String contactPhone
) {
}
