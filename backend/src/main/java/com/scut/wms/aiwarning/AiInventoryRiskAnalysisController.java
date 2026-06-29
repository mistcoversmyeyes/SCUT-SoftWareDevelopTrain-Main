package com.scut.wms.aiwarning;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-warning/analysis")
public class AiInventoryRiskAnalysisController {
    private final AiInventoryRiskAnalysisService service;
    private final AiInventoryAdviceReportService reportService;

    public AiInventoryRiskAnalysisController(
            AiInventoryRiskAnalysisService service,
            AiInventoryAdviceReportService reportService
    ) {
        this.service = service;
        this.reportService = reportService;
    }

    @GetMapping("/inventory-risks")
    public AiInventoryRiskAnalysisResponse inventoryRisks() {
        return service.analyzeInventoryRisks();
    }

    @GetMapping("/inventory-risk-report")
    public AiInventoryAdviceReportResponse inventoryRiskReport() {
        return reportService.generateInventoryRiskReport();
    }
}
