package com.scut.wms.lock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("inventory_lock")
public class InventoryLock {
    public static final String LOCKED = "LOCKED";
    public static final String RELEASED = "RELEASED";
    public static final String FORCE_STOLEN = "FORCE_STOLEN";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long outboundOrderId;
    private Long outboundOrderLineId;
    private Long inventoryTagId;
    private Long materialId;
    private BigDecimal lockQty;
    private String status;
    private Long stolenByOrderId;
    private LocalDateTime stolenAt;
    private LocalDateTime unlockedAt;
    private String unlockedBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOutboundOrderId() { return outboundOrderId; }
    public void setOutboundOrderId(Long outboundOrderId) { this.outboundOrderId = outboundOrderId; }
    public Long getOutboundOrderLineId() { return outboundOrderLineId; }
    public void setOutboundOrderLineId(Long outboundOrderLineId) { this.outboundOrderLineId = outboundOrderLineId; }
    public Long getInventoryTagId() { return inventoryTagId; }
    public void setInventoryTagId(Long inventoryTagId) { this.inventoryTagId = inventoryTagId; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public BigDecimal getLockQty() { return lockQty; }
    public void setLockQty(BigDecimal lockQty) { this.lockQty = lockQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getStolenByOrderId() { return stolenByOrderId; }
    public void setStolenByOrderId(Long stolenByOrderId) { this.stolenByOrderId = stolenByOrderId; }
    public LocalDateTime getStolenAt() { return stolenAt; }
    public void setStolenAt(LocalDateTime stolenAt) { this.stolenAt = stolenAt; }
    public LocalDateTime getUnlockedAt() { return unlockedAt; }
    public void setUnlockedAt(LocalDateTime unlockedAt) { this.unlockedAt = unlockedAt; }
    public String getUnlockedBy() { return unlockedBy; }
    public void setUnlockedBy(String unlockedBy) { this.unlockedBy = unlockedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
