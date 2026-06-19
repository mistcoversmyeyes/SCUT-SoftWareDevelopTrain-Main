package com.scut.wms.outbound;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("outbound_order_line")
public class OutboundOrderLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long outboundOrderId;
    private Integer lineNo;
    private Long materialId;
    private Long supplierId;
    private BigDecimal plannedQty;
    private BigDecimal pickedQty;
    private Long targetWarehouseId;
    private Long targetLocationId;
    private Long containerTypeId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOutboundOrderId() { return outboundOrderId; }
    public void setOutboundOrderId(Long outboundOrderId) { this.outboundOrderId = outboundOrderId; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public BigDecimal getPlannedQty() { return plannedQty; }
    public void setPlannedQty(BigDecimal plannedQty) { this.plannedQty = plannedQty; }
    public BigDecimal getPickedQty() { return pickedQty; }
    public void setPickedQty(BigDecimal pickedQty) { this.pickedQty = pickedQty; }
    public Long getTargetWarehouseId() { return targetWarehouseId; }
    public void setTargetWarehouseId(Long targetWarehouseId) { this.targetWarehouseId = targetWarehouseId; }
    public Long getTargetLocationId() { return targetLocationId; }
    public void setTargetLocationId(Long targetLocationId) { this.targetLocationId = targetLocationId; }
    public Long getContainerTypeId() { return containerTypeId; }
    public void setContainerTypeId(Long containerTypeId) { this.containerTypeId = containerTypeId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
