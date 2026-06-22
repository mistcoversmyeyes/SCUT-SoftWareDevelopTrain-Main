package com.scut.wms.aiwarning;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/ai-warning/imports")
public class InventoryFlowHistoryImportController {
    private final InventoryFlowHistoryImportService service;

    public InventoryFlowHistoryImportController(InventoryFlowHistoryImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/inventory-flow-history", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InventoryFlowHistoryImportResponse importInventoryFlowHistory(@RequestPart("file") MultipartFile file) {
        return service.importInventoryFlowHistory(file);
    }

    @GetMapping("/inventory-flow-history/batches")
    public List<InventoryFlowHistoryBatchView> listBatches() {
        return service.listBatches();
    }

    @GetMapping("/inventory-flow-history/records")
    public List<InventoryFlowHistoryRecordView> listRecords(
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String movementType
    ) {
        return service.listRecords(batchId, materialCode, movementType);
    }
}
