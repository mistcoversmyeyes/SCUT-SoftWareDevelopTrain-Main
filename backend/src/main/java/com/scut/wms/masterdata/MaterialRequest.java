package com.scut.wms.masterdata;

import java.math.BigDecimal;

public record MaterialRequest(
        String materialCode,
        String materialName,
        String specification,
        String unit,
        Long supplierId,
        Long containerTypeId,
        BigDecimal lowStockQty,
        BigDecimal highStockQty
) {
}
