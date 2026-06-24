package com.scut.wms.inventory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class InventoryMovementView {
    private String movementNo;
    private String movementType;
    private String materialCode;
    private String materialName;
    private String warehouseCode;
    private String warehouseName;
    private String locationCode;
    private String locationName;
    private String plannedLocationCode;
    private String plannedLocationName;
    private BigDecimal qty;
    private String inventoryTagCode;
    private String inboundNo;
    private LocalDateTime occurredAt;

    public String getMovementNo() {
        return movementNo;
    }

    public void setMovementNo(String movementNo) {
        this.movementNo = movementNo;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
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

    public String getPlannedLocationCode() {
        return plannedLocationCode;
    }

    public void setPlannedLocationCode(String plannedLocationCode) {
        this.plannedLocationCode = plannedLocationCode;
    }

    public String getPlannedLocationName() {
        return plannedLocationName;
    }

    public void setPlannedLocationName(String plannedLocationName) {
        this.plannedLocationName = plannedLocationName;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getInventoryTagCode() {
        return inventoryTagCode;
    }

    public void setInventoryTagCode(String inventoryTagCode) {
        this.inventoryTagCode = inventoryTagCode;
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
