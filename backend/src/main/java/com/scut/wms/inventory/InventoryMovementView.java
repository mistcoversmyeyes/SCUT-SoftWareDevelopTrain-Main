package com.scut.wms.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InventoryMovementView {
    private String movementNo;
    private String materialCode;
    private String materialName;
    private String warehouseCode;
    private String warehouseName;
    private String locationCode;
    private String locationName;
    private BigDecimal qty;
    private String kanbanCode;
    private String inboundNo;
    private LocalDateTime occurredAt;

    public String getMovementNo() {
        return movementNo;
    }

    public void setMovementNo(String movementNo) {
        this.movementNo = movementNo;
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

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getKanbanCode() {
        return kanbanCode;
    }

    public void setKanbanCode(String kanbanCode) {
        this.kanbanCode = kanbanCode;
    }

    public String getInboundNo() {
        return inboundNo;
    }

    public void setInboundNo(String inboundNo) {
        this.inboundNo = inboundNo;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
