package com.scut.wms.inventory;

public class InventoryTagTraceView {
    private String inventoryTagCode;
    private String inventoryTagStatus;
    private String inboundNo;
    private String materialCode;
    private String materialName;
    private String locationCode;
    private String locationName;
    private String scannedAt;
    private String movementNo;

    public String getInventoryTagCode() {
        return inventoryTagCode;
    }

    public void setInventoryTagCode(String inventoryTagCode) {
        this.inventoryTagCode = inventoryTagCode;
    }

    public String getInventoryTagStatus() {
        return inventoryTagStatus;
    }

    public void setInventoryTagStatus(String inventoryTagStatus) {
        this.inventoryTagStatus = inventoryTagStatus;
    }

    public String getInboundNo() {
        return inboundNo;
    }

    public void setInboundNo(String inboundNo) {
        this.inboundNo = inboundNo;
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

    public String getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(String scannedAt) {
        this.scannedAt = scannedAt;
    }

    public String getMovementNo() {
        return movementNo;
    }

    public void setMovementNo(String movementNo) {
        this.movementNo = movementNo;
    }
}
