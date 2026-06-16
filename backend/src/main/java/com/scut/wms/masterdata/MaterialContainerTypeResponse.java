package com.scut.wms.masterdata;

import java.math.BigDecimal;

public record MaterialContainerTypeResponse(
    Long id,
    String containerCode,
    String containerName,
    BigDecimal capacityQty,
    boolean isDefault
) {}
