package com.scut.wms.masterdata;

import java.util.List;

public record MasterDataOptionsResponse(
        List<OptionItem> suppliers,
        List<OptionItem> materials,
        List<OptionItem> warehouses,
        List<LocationOption> locations
) {
}
