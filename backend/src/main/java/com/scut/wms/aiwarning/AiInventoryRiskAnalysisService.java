package com.scut.wms.aiwarning;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.inventory.InventoryBalanceView;
import com.scut.wms.inventory.InventoryTransactionMapper;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AiInventoryRiskAnalysisService {
    private static final int DEFAULT_LEAD_TIME_DAYS = 7;
    private static final int DEFAULT_SAFETY_STOCK_DAYS = 3;
    private static final int DEFAULT_STAGNANT_DAYS = 45;
    private static final Set<String> HIGH_QUALITY_STATUSES = Set.of("HOLD", "NEAR_EXPIRY");
    private static final Set<String> EXPIRED_QUALITY_STATUSES = Set.of("EXPIRED");

    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryFlowHistoryRecordMapper flowRecordMapper;
    private final MaterialMapper materialMapper;

    public AiInventoryRiskAnalysisService(
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryFlowHistoryRecordMapper flowRecordMapper,
            MaterialMapper materialMapper
    ) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.flowRecordMapper = flowRecordMapper;
        this.materialMapper = materialMapper;
    }

    public AiInventoryRiskAnalysisResponse analyzeInventoryRisks() {
        LocalDate snapshotDate = LocalDate.now();
        List<InventoryBalanceView> balances = inventoryTransactionMapper.selectInventoryBalances(null, null, null);
        List<InventoryFlowHistoryRecord> records = flowRecordMapper.selectList(Wrappers.<InventoryFlowHistoryRecord>lambdaQuery()
                .orderByDesc(InventoryFlowHistoryRecord::getBusinessDate)
                .orderByDesc(InventoryFlowHistoryRecord::getRowNumber));
        Map<String, FlowInsight> insights = buildFlowInsights(records, snapshotDate);
        Map<String, Material> materials = materialMapper.selectList(null).stream()
                .filter(item -> StringUtils.hasText(item.getMaterialCode()))
                .collect(Collectors.toMap(Material::getMaterialCode, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<AiInventoryRiskRow> rows = balances.stream()
                .map(balance -> buildRow(balance, materials.get(balance.getMaterialCode()), insights.get(balance.getMaterialCode()), snapshotDate))
                .sorted(Comparator
                        .comparingInt((AiInventoryRiskRow row) -> riskScore(row.shortageRisk()))
                        .thenComparingInt(row -> riskScore(row.stagnationRisk()))
                        .thenComparingInt(row -> riskScore(row.qualityRisk()))
                        .reversed()
                        .thenComparing(AiInventoryRiskRow::materialCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        return new AiInventoryRiskAnalysisResponse(
                "内部 WMS 数据驱动的缺货、呆滞、质量/报废风险识别",
                readinessCode(records),
                readinessLabel(records),
                readinessReason(records),
                snapshotDate,
                LocalDateTime.now(),
                summarize(rows),
                rows
        );
    }

    private Map<String, FlowInsight> buildFlowInsights(List<InventoryFlowHistoryRecord> records, LocalDate snapshotDate) {
        Map<String, FlowInsight> insights = new LinkedHashMap<>();
        for (InventoryFlowHistoryRecord record : records) {
            if (!StringUtils.hasText(record.getMaterialCode())) {
                continue;
            }
            FlowInsight insight = insights.computeIfAbsent(record.getMaterialCode(), FlowInsight::new);
            insight.accept(record, snapshotDate);
        }
        return insights;
    }

    private AiInventoryRiskRow buildRow(InventoryBalanceView balance, Material material, FlowInsight insight, LocalDate snapshotDate) {
        BigDecimal availableQty = value(balance.getAvailableQty());
        BigDecimal onHandQty = value(balance.getOnHandQty());
        BigDecimal lockedQty = value(balance.getOutboundLockedQty()).add(value(balance.getManualLockedQty()));
        BigDecimal sealedQty = value(balance.getSealedQty());
        BigDecimal lowStockQty = value(material == null ? null : material.getLowStockQty());
        BigDecimal avgDailyOutbound7d = insight == null ? BigDecimal.ZERO : divide(insight.outbound7d, BigDecimal.valueOf(7));
        BigDecimal avgDailyOutbound30d = insight == null ? BigDecimal.ZERO : divide(insight.outbound30d, BigDecimal.valueOf(30));
        BigDecimal dailyUse = avgDailyOutbound7d.max(avgDailyOutbound30d);
        BigDecimal daysOfCover = dailyUse.compareTo(BigDecimal.ZERO) > 0 ? divide(availableQty, dailyUse) : null;
        Integer daysSinceLastOutbound = daysBetween(snapshotDate, insight == null ? null : insight.lastOutboundDate);
        Integer inventoryAgeDays = daysBetween(snapshotDate, insight == null ? null : insight.lastInboundDate);

        return new AiInventoryRiskRow(
                balance.getMaterialCode(),
                balance.getMaterialName(),
                balance.getWarehouseCode(),
                balance.getLocationCode(),
                onHandQty,
                availableQty,
                lockedQty,
                sealedQty,
                avgDailyOutbound7d,
                avgDailyOutbound30d,
                daysOfCover,
                insight == null ? null : insight.lastInboundDate,
                insight == null ? null : insight.lastOutboundDate,
                inventoryAgeDays,
                daysSinceLastOutbound,
                insight == null ? null : insight.latestQualityStatus,
                shortageRisk(availableQty, lowStockQty, dailyUse, daysOfCover, insight),
                stagnationRisk(onHandQty, insight, inventoryAgeDays, daysSinceLastOutbound),
                qualityRisk(insight)
        );
    }

    private AiInventoryRiskLevel shortageRisk(
            BigDecimal availableQty,
            BigDecimal lowStockQty,
            BigDecimal dailyUse,
            BigDecimal daysOfCover,
            FlowInsight insight
    ) {
        if (availableQty.compareTo(BigDecimal.ZERO) <= 0) {
            return level("CRITICAL", "紧急", "danger", "当前可用库存为 0，需要立即补货或释放占用。");
        }
        if (insight == null || insight.totalRecords == 0) {
            return level("DATA_UNPREPARED", "数据未准备", "info", "尚未导入 inventory_flow_history，缺货风险只能降级判断。");
        }
        int replenishmentWindow = DEFAULT_LEAD_TIME_DAYS + DEFAULT_SAFETY_STOCK_DAYS;
        if (daysOfCover != null && daysOfCover.compareTo(BigDecimal.valueOf(replenishmentWindow)) <= 0) {
            return level("HIGH", "高风险", "danger", "库存覆盖约 " + format(daysOfCover) + " 天，低于补货窗口 " + replenishmentWindow + " 天。");
        }
        if (lowStockQty.compareTo(BigDecimal.ZERO) > 0 && availableQty.compareTo(lowStockQty) <= 0) {
            return level("WATCH", "关注", "warning", "当前可用 " + format(availableQty) + "，已低于低储阈值 " + format(lowStockQty) + "。");
        }
        if (dailyUse.compareTo(BigDecimal.ZERO) == 0) {
            return level("NONE", "正常", "success", "近期没有出库消耗，暂未触发缺货规则。");
        }
        return level("NONE", "正常", "success", "库存覆盖约 " + format(daysOfCover) + " 天。");
    }

    private AiInventoryRiskLevel stagnationRisk(
            BigDecimal onHandQty,
            FlowInsight insight,
            Integer inventoryAgeDays,
            Integer daysSinceLastOutbound
    ) {
        if (onHandQty.compareTo(BigDecimal.ZERO) <= 0) {
            return level("NONE", "正常", "success", "当前账面库存为 0，不参与呆滞判断。");
        }
        if (insight == null || insight.totalRecords == 0) {
            return level("DATA_UNPREPARED", "数据未准备", "info", "尚未导入 inventory_flow_history，无法判断最近出库活跃度。");
        }
        if ((daysSinceLastOutbound != null && daysSinceLastOutbound >= DEFAULT_STAGNANT_DAYS && insight.outbound30d.compareTo(BigDecimal.ZERO) == 0)
                || (daysSinceLastOutbound == null && inventoryAgeDays != null && inventoryAgeDays >= DEFAULT_STAGNANT_DAYS)) {
            int stagnantDays = daysSinceLastOutbound == null ? inventoryAgeDays : daysSinceLastOutbound;
            return level("HIGH", "高风险", "danger", "最近 " + stagnantDays + " 天未见有效出库，超过默认呆滞阈值 " + DEFAULT_STAGNANT_DAYS + " 天。");
        }
        if (inventoryAgeDays != null && inventoryAgeDays >= 30
                && insight.outbound30d.compareTo(onHandQty.multiply(new BigDecimal("0.3"))) < 0) {
            return level("WATCH", "关注", "warning", "库存账龄约 " + inventoryAgeDays + " 天，近 30 天出库 " + format(insight.outbound30d) + "，周转偏慢。");
        }
        return level("NONE", "正常", "success", "近 30 天仍有正常流转，未触发呆滞规则。");
    }

    private AiInventoryRiskLevel qualityRisk(FlowInsight insight) {
        if (insight == null || insight.totalRecords == 0) {
            return level("DATA_UNPREPARED", "数据未准备", "info", "尚未导入质量状态字段，报废/失效风险暂不计算。");
        }
        if (insight.scrapRecords > 0 || EXPIRED_QUALITY_STATUSES.contains(insight.latestQualityStatus)) {
            return level("EXPIRED", "已失效", "danger", "导入流水包含过期质量状态或 SCRAP 报废记录。");
        }
        if (HIGH_QUALITY_STATUSES.contains(insight.latestQualityStatus)) {
            return level("HIGH", "高风险", "danger", "最新质量状态为 " + insight.latestQualityStatus + "，需优先复核封存、近效期或待检库存。");
        }
        return level("NONE", "正常", "success", "未发现导入样例中的质量/报废风险标记。");
    }

    private AiInventoryRiskSummary summarize(List<AiInventoryRiskRow> rows) {
        return new AiInventoryRiskSummary(
                rows.size(),
                count(rows, "HIGH", AiInventoryRiskRow::shortageRisk),
                count(rows, "CRITICAL", AiInventoryRiskRow::shortageRisk),
                count(rows, "HIGH", AiInventoryRiskRow::stagnationRisk),
                count(rows, "HIGH", AiInventoryRiskRow::qualityRisk),
                count(rows, "EXPIRED", AiInventoryRiskRow::qualityRisk),
                (int) rows.stream().filter(row -> Objects.equals("DATA_UNPREPARED", row.shortageRisk().code())
                        || Objects.equals("DATA_UNPREPARED", row.stagnationRisk().code())
                        || Objects.equals("DATA_UNPREPARED", row.qualityRisk().code())).count()
        );
    }

    private int count(List<AiInventoryRiskRow> rows, String code, java.util.function.Function<AiInventoryRiskRow, AiInventoryRiskLevel> getter) {
        return (int) rows.stream().filter(row -> Objects.equals(code, getter.apply(row).code())).count();
    }

    private String readinessCode(List<InventoryFlowHistoryRecord> records) {
        if (records.isEmpty()) {
            return "NOT_READY";
        }
        boolean hasOutbound = records.stream().anyMatch(record -> "OUTBOUND".equals(record.getMovementType()));
        return hasOutbound ? "READY" : "PARTIAL";
    }

    private String readinessLabel(List<InventoryFlowHistoryRecord> records) {
        return switch (readinessCode(records)) {
            case "READY" -> "已准备";
            case "PARTIAL" -> "部分准备";
            default -> "数据未准备";
        };
    }

    private String readinessReason(List<InventoryFlowHistoryRecord> records) {
        return switch (readinessCode(records)) {
            case "READY" -> "已导入 " + records.size() + " 条样例流水，可进行规则型缺货、呆滞和质量风险分析。";
            case "PARTIAL" -> "已有样例流水但缺少 OUTBOUND 出库记录，缺货/呆滞规则只能部分计算。";
            default -> "尚未导入 inventory_flow_history，预警结果会降级为“数据未准备”。";
        };
    }

    private int riskScore(AiInventoryRiskLevel risk) {
        return switch (risk.code()) {
            case "CRITICAL", "EXPIRED" -> 5;
            case "HIGH" -> 4;
            case "WATCH" -> 3;
            case "DATA_UNPREPARED" -> 2;
            default -> 1;
        };
    }

    private AiInventoryRiskLevel level(String code, String label, String tone, String reason) {
        return new AiInventoryRiskLevel(code, label, tone, reason);
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return left.divide(right, 1, RoundingMode.HALF_UP);
    }

    private String format(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Integer daysBetween(LocalDate later, LocalDate earlier) {
        if (later == null || earlier == null) {
            return null;
        }
        return Math.max((int) ChronoUnit.DAYS.between(earlier, later), 0);
    }

    private static class FlowInsight {
        private final String materialCode;
        private int totalRecords;
        private BigDecimal outbound7d = BigDecimal.ZERO;
        private BigDecimal outbound30d = BigDecimal.ZERO;
        private LocalDate lastInboundDate;
        private LocalDate lastOutboundDate;
        private LocalDate lastMovementDate;
        private String latestQualityStatus;
        private int scrapRecords;

        private FlowInsight(String materialCode) {
            this.materialCode = materialCode;
        }

        private void accept(InventoryFlowHistoryRecord record, LocalDate snapshotDate) {
            totalRecords += 1;
            LocalDate businessDate = record.getBusinessDate();
            String movementType = normalize(record.getMovementType());
            BigDecimal quantity = record.getQuantity() == null ? BigDecimal.ZERO : record.getQuantity();
            if (businessDate != null && (lastMovementDate == null || businessDate.isAfter(lastMovementDate))) {
                lastMovementDate = businessDate;
                latestQualityStatus = normalize(record.getQualityStatus());
            }
            if ("OUTBOUND".equals(movementType)) {
                if (businessDate != null && (lastOutboundDate == null || businessDate.isAfter(lastOutboundDate))) {
                    lastOutboundDate = businessDate;
                }
                if (withinDays(snapshotDate, businessDate, 7)) {
                    outbound7d = outbound7d.add(quantity);
                }
                if (withinDays(snapshotDate, businessDate, 30)) {
                    outbound30d = outbound30d.add(quantity);
                }
            }
            if ("INBOUND".equals(movementType) && businessDate != null && (lastInboundDate == null || businessDate.isAfter(lastInboundDate))) {
                lastInboundDate = businessDate;
            }
            if ("SCRAP".equals(movementType)) {
                scrapRecords += 1;
            }
        }

        private boolean withinDays(LocalDate snapshotDate, LocalDate businessDate, int days) {
            if (snapshotDate == null || businessDate == null || businessDate.isAfter(snapshotDate)) {
                return false;
            }
            return ChronoUnit.DAYS.between(businessDate, snapshotDate) < days;
        }

        private String normalize(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim().toUpperCase(Locale.ROOT);
        }
    }
}
