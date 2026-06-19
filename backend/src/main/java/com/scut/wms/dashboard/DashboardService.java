package com.scut.wms.dashboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scut.wms.inbound.InboundOrder;
import com.scut.wms.inbound.InboundOrderMapper;
import com.scut.wms.inventory.InventoryBalance;
import com.scut.wms.inventory.InventoryBalanceMapper;
import com.scut.wms.masterdata.Material;
import com.scut.wms.masterdata.MaterialMapper;
import com.scut.wms.outbound.OutboundOrder;
import com.scut.wms.outbound.OutboundOrderMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final MaterialMapper materialMapper;
    private final InventoryBalanceMapper inventoryBalanceMapper;

    public DashboardService(
            InboundOrderMapper inboundOrderMapper,
            OutboundOrderMapper outboundOrderMapper,
            MaterialMapper materialMapper,
            InventoryBalanceMapper inventoryBalanceMapper
    ) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.materialMapper = materialMapper;
        this.inventoryBalanceMapper = inventoryBalanceMapper;
    }

    public DashboardStatsResponse getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        long todayInboundCount = inboundOrderMapper.selectCount(Wrappers.<InboundOrder>lambdaQuery()
                .ge(InboundOrder::getCreatedAt, todayStart)
                .le(InboundOrder::getCreatedAt, todayEnd));

        long todayOutboundCount = outboundOrderMapper.selectCount(Wrappers.<OutboundOrder>lambdaQuery()
                .ge(OutboundOrder::getCreatedAt, todayStart)
                .le(OutboundOrder::getCreatedAt, todayEnd));

        long totalMaterials = materialMapper.selectCount(Wrappers.emptyWrapper());

        long pendingOrders = inboundOrderMapper.selectCount(Wrappers.<InboundOrder>lambdaQuery()
                .in(InboundOrder::getStatus, "DRAFT", "RELEASED", "PARTIAL_RECEIVED"));

        List<DashboardStatsResponse.LowStockAlert> lowStockAlerts = new ArrayList<>();
        List<DashboardStatsResponse.HighStockAlert> highStockAlerts = new ArrayList<>();

        // Query balances with material low/high stock info
        List<InventoryBalance> balances = inventoryBalanceMapper.selectList(Wrappers.emptyWrapper());
        for (InventoryBalance b : balances) {
            Material m = materialMapper.selectById(b.getMaterialId());
            if (m == null) continue;

            if (m.getLowStockQty() != null && b.getOnHandQty().compareTo(m.getLowStockQty()) < 0) {
                lowStockAlerts.add(toLowStockAlert(b, m));
            }
            if (m.getHighStockQty() != null && b.getOnHandQty().compareTo(m.getHighStockQty()) > 0) {
                highStockAlerts.add(toHighStockAlert(b, m));
            }
        }

        return new DashboardStatsResponse(
                todayInboundCount,
                todayOutboundCount,
                totalMaterials,
                pendingOrders,
                lowStockAlerts,
                highStockAlerts
        );
    }

    private DashboardStatsResponse.LowStockAlert toLowStockAlert(InventoryBalance balance, Material material) {
        return new DashboardStatsResponse.LowStockAlert(
                material.getMaterialCode(),
                material.getMaterialName(),
                null, null, null, null,
                balance.getOnHandQty(),
                material.getLowStockQty()
        );
    }

    private DashboardStatsResponse.HighStockAlert toHighStockAlert(InventoryBalance balance, Material material) {
        return new DashboardStatsResponse.HighStockAlert(
                material.getMaterialCode(),
                material.getMaterialName(),
                null, null, null, null,
                balance.getOnHandQty(),
                material.getHighStockQty()
        );
    }
}
