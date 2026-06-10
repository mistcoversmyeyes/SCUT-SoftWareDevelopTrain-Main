package com.scut.wms.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ScanKanbanContext {
    private Long kanbanId;
    private String kanbanCode;
    private String kanbanStatus;
    private BigDecimal boardQty;
    private LocalDateTime receivedAt;
    private Long orderId;
    private String inboundNo;
    private String orderStatus;
    private Long lineId;
    private Integer lineNo;
    private BigDecimal plannedQty;
    private BigDecimal lineReceivedQty;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long targetWarehouseId;
    private Long targetLocationId;
    private Long locationWarehouseId;
    private String locationName;

    public Long getKanbanId() {
        return kanbanId;
    }

    public void setKanbanId(Long kanbanId) {
        this.kanbanId = kanbanId;
    }

    public String getKanbanCode() {
        return kanbanCode;
    }

    public void setKanbanCode(String kanbanCode) {
        this.kanbanCode = kanbanCode;
    }

    public String getKanbanStatus() {
        return kanbanStatus;
    }

    public void setKanbanStatus(String kanbanStatus) {
        this.kanbanStatus = kanbanStatus;
    }

    public BigDecimal getBoardQty() {
        return boardQty;
    }

    public void setBoardQty(BigDecimal boardQty) {
        this.boardQty = boardQty;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getInboundNo() {
        return inboundNo;
    }

    public void setInboundNo(String inboundNo) {
        this.inboundNo = inboundNo;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Long getLineId() {
        return lineId;
    }

    public void setLineId(Long lineId) {
        this.lineId = lineId;
    }

    public Integer getLineNo() {
        return lineNo;
    }

    public void setLineNo(Integer lineNo) {
        this.lineNo = lineNo;
    }

    public BigDecimal getPlannedQty() {
        return plannedQty;
    }

    public void setPlannedQty(BigDecimal plannedQty) {
        this.plannedQty = plannedQty;
    }

    public BigDecimal getLineReceivedQty() {
        return lineReceivedQty;
    }

    public void setLineReceivedQty(BigDecimal lineReceivedQty) {
        this.lineReceivedQty = lineReceivedQty;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public void setMaterialCode(String materialCode) {
        this.materialCode = materialCode;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public Long getTargetWarehouseId() {
        return targetWarehouseId;
    }

    public void setTargetWarehouseId(Long targetWarehouseId) {
        this.targetWarehouseId = targetWarehouseId;
    }

    public Long getTargetLocationId() {
        return targetLocationId;
    }

    public void setTargetLocationId(Long targetLocationId) {
        this.targetLocationId = targetLocationId;
    }

    public Long getLocationWarehouseId() {
        return locationWarehouseId;
    }

    public void setLocationWarehouseId(Long locationWarehouseId) {
        this.locationWarehouseId = locationWarehouseId;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }
}
