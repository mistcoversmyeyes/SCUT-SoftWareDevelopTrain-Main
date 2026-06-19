package com.scut.wms.container;

import java.math.BigDecimal;

public record ContainerTypeRequest(
        String containerCode,
        String containerName,
        BigDecimal capacityQty
) {
}
