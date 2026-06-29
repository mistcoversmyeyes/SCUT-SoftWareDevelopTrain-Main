package com.scut.wms.aiwarning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiInventoryAdviceReportService {
    private static final int MAX_RISK_ROWS = 12;
    private static final int MAX_API_ATTEMPTS = 2;

    private final AiInventoryRiskAnalysisService analysisService;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String apiFormat;

    public AiInventoryAdviceReportService(
            AiInventoryRiskAnalysisService analysisService,
            ObjectMapper objectMapper,
            @Value("${wms.ai.api-key:}") String apiKey,
            @Value("${wms.ai.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${wms.ai.model:deepseek-v4-flash}") String model,
            @Value("${wms.ai.api-format:auto}") String apiFormat
    ) {
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.model = model;
        this.apiFormat = StringUtils.hasText(apiFormat) ? apiFormat.trim().toLowerCase() : "auto";
    }

    public AiInventoryAdviceReportResponse generateInventoryRiskReport() {
        AiInventoryRiskAnalysisResponse analysis = analysisService.analyzeInventoryRisks();
        if (!StringUtils.hasText(apiKey)) {
            return new AiInventoryAdviceReportResponse(
                    "NOT_CONFIGURED",
                    false,
                    "openai-compatible",
                    model,
                    LocalDateTime.now(),
                    analysis.summary(),
                    null,
                    "未配置 WMS_AI_API_KEY，已完成规则型风险分析，但无法调用 AI API 生成建议报告。"
            );
        }

        try {
            String report = requestReport(analysis);
            return new AiInventoryAdviceReportResponse(
                    "GENERATED",
                    true,
                    "openai-compatible",
                    model,
                    LocalDateTime.now(),
                    analysis.summary(),
                    report,
                    "AI 建议报告已生成。"
            );
        } catch (Exception exception) {
            return new AiInventoryAdviceReportResponse(
                    "FAILED",
                    true,
                    "openai-compatible",
                    model,
                    LocalDateTime.now(),
                    analysis.summary(),
                    null,
                    "AI API 调用失败：" + exception.getMessage()
            );
        }
    }

    private String requestReport(AiInventoryRiskAnalysisResponse analysis) throws Exception {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();

        String responseBody = requestWithRetry(client, analysis);

        String text = shouldUseChatCompletions()
                ? extractChatCompletionText(responseBody)
                : extractResponseOutputText(responseBody);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("AI API 未返回可读文本");
        }
        return text.trim();
    }

    private String requestWithRetry(RestClient client, AiInventoryRiskAnalysisResponse analysis) throws InterruptedException {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_API_ATTEMPTS; attempt++) {
            try {
                return shouldUseChatCompletions()
                        ? requestChatCompletions(client, analysis)
                        : requestResponses(client, analysis);
            } catch (RuntimeException exception) {
                lastException = exception;
                if (attempt < MAX_API_ATTEMPTS) {
                    Thread.sleep(800L);
                }
            }
        }
        throw lastException;
    }

    private String requestResponses(RestClient client, AiInventoryRiskAnalysisResponse analysis) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", buildPrompt(analysis));
        payload.put("max_output_tokens", 2200);

        try {
            return client.post()
                    .uri("/responses")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private String requestChatCompletions(RestClient client, AiInventoryRiskAnalysisResponse analysis) {
        Map<String, Object> systemMessage = new LinkedHashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "只输出最终中文 Markdown 报告，不要输出分析过程、推理过程或寒暄。");

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", buildPrompt(analysis));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(systemMessage, userMessage));
        payload.put("max_tokens", 2200);

        try {
            return client.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private String buildPrompt(AiInventoryRiskAnalysisResponse analysis) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                你是汽车零部件 WMS 仓储系统的预警分析助手。
                你只根据输入的规则型风险结果生成报告，不要声称已经训练机器学习模型。
                输出中文 Markdown，面向仓库主管和值班操作员，可直接用于晨会和现场处理。
                结构固定为：
                # AI 预警建议报告
                ## 1. 今日风险概览
                - 用 3-5 条短句说明总体风险，不超过 120 字。
                ## 2. 优先处理清单
                - 用 Markdown 表格输出，列为：优先级、物料/库位、风险类型、触发依据、建议负责人、完成时限。
                - 优先级只能使用 P0、P1、P2；P0 表示今天立即处理，P1 表示 24 小时内处理，P2 表示本周跟进。
                ## 3. 建议动作
                - 按缺货、呆滞、质量三类分别给出可执行动作。
                ## 4. 需要人工确认的数据缺口
                - 明确列出哪些判断依赖缺失字段或未导入流水。
                ## 5. 查看与流转建议
                - 告诉用户可在页面下方查看报告，可复制给仓库主管、采购、质检协同处理。
                每条建议必须可回溯到输入中的物料、数量、风险等级或触发原因。
                不要编造外部市场、天气、新闻、供应商实时状态。
                不要输出与仓储处理无关的长篇解释。
                直接从 "# AI 预警建议报告" 开始输出，不要输出“好的”、思考过程或任务复述。

                """);
        builder.append("报告生成时间：").append(analysis.generatedAt()).append("\n");
        builder.append("数据准备状态：").append(analysis.readinessCode()).append(" - ").append(analysis.readinessReason()).append("\n");
        builder.append("汇总：")
                .append("分析对象 ").append(analysis.summary().materialLocationCount())
                .append("，缺货高风险 ").append(analysis.summary().shortageHighCount())
                .append("，缺货紧急 ").append(analysis.summary().shortageCriticalCount())
                .append("，呆滞高风险 ").append(analysis.summary().stagnationHighCount())
                .append("，质量高风险 ").append(analysis.summary().qualityHighCount())
                .append("，已失效 ").append(analysis.summary().qualityExpiredCount())
                .append("，数据未准备 ").append(analysis.summary().dataUnpreparedCount())
                .append("。\n\n");
        builder.append("高优先级明细：\n");

        List<AiInventoryRiskRow> rows = analysis.rows().stream()
                .filter(this::shouldInclude)
                .limit(MAX_RISK_ROWS)
                .toList();
        if (rows.isEmpty()) {
            builder.append("- 暂无高优先级风险行。\n");
        } else {
            for (AiInventoryRiskRow row : rows) {
                builder.append("- 物料 ").append(row.materialCode())
                        .append(" / ").append(row.materialName())
                        .append("，仓库 ").append(row.warehouseCode())
                        .append("，库位 ").append(row.locationCode())
                        .append("，可用 ").append(format(row.availableQty()))
                        .append("，账面 ").append(format(row.onHandQty()))
                        .append("，覆盖天数 ").append(format(row.daysOfCover()))
                        .append("，缺货风险 ").append(row.shortageRisk().code()).append("：").append(row.shortageRisk().reason())
                        .append("，呆滞风险 ").append(row.stagnationRisk().code()).append("：").append(row.stagnationRisk().reason())
                        .append("，质量风险 ").append(row.qualityRisk().code()).append("：").append(row.qualityRisk().reason())
                        .append("\n");
            }
        }
        return builder.toString();
    }

    private boolean shouldInclude(AiInventoryRiskRow row) {
        return isVisibleRisk(row.shortageRisk())
                || isVisibleRisk(row.stagnationRisk())
                || isVisibleRisk(row.qualityRisk());
    }

    private boolean isVisibleRisk(AiInventoryRiskLevel risk) {
        return switch (risk.code()) {
            case "WATCH", "HIGH", "CRITICAL", "EXPIRED", "DATA_UNPREPARED" -> true;
            default -> false;
        };
    }

    private String extractResponseOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String outputText = root.path("output_text").asText(null);
        if (StringUtils.hasText(outputText)) {
            return outputText;
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode outputItem : root.path("output")) {
            for (JsonNode contentItem : outputItem.path("content")) {
                String text = contentItem.path("text").asText(null);
                if (StringUtils.hasText(text)) {
                    builder.append(text).append("\n");
                }
            }
        }
        return builder.toString();
    }

    private String extractChatCompletionText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode firstChoice = root.path("choices").path(0);
        String content = firstChoice.path("message").path("content").asText(null);
        if (StringUtils.hasText(content)) {
            return normalizeReportText(content);
        }
        String reasoningContent = firstChoice.path("message").path("reasoning_content").asText(null);
        if (StringUtils.hasText(reasoningContent)) {
            return extractFinalReportFromReasoning(reasoningContent);
        }
        String deltaContent = firstChoice.path("delta").path("content").asText(null);
        if (StringUtils.hasText(deltaContent)) {
            return normalizeReportText(deltaContent);
        }
        return normalizeReportText(firstChoice.path("text").asText(""));
    }

    private String normalizeReportText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int headerIndex = trimmed.indexOf("# AI 预警建议报告");
        if (headerIndex >= 0) {
            return trimmed.substring(headerIndex).trim();
        }
        return trimmed;
    }

    private String extractFinalReportFromReasoning(String reasoningContent) {
        String normalized = normalizeReportText(reasoningContent);
        if (normalized.startsWith("# AI 预警建议报告")) {
            return normalized;
        }
        return "";
    }

    private boolean shouldUseChatCompletions() {
        if ("chat-completions".equals(apiFormat) || "chat".equals(apiFormat)) {
            return true;
        }
        if ("responses".equals(apiFormat)) {
            return false;
        }
        return baseUrl.contains("api.deepseek.com");
    }

    private String format(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "https://api.openai.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
