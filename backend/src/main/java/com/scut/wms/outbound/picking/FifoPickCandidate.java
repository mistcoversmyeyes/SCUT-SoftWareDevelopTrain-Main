package com.scut.wms.outbound.picking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class FifoPickCandidate {
    private Long inventoryTagId;
    private String inventoryTagCode;
    private BigDecimal boardQty;
    private BigDecimal pickedQty;
    private LocalDateTime receivedAt;
    private Long materialId;
    private Long warehouseId;
    private Long storageLocationId;

    public Long getInventoryTagId() { return inventoryTagId; }
    public void setInventoryTagId(Long inventoryTagId) { this.inventoryTagId = inventoryTagId; }
    public String getInventoryTagCode() { return inventoryTagCode; }
    public void setInventoryTagCode(String inventoryTagCode) { this.inventoryTagCode = inventoryTagCode; }
    public BigDecimal getBoardQty() { return boardQty; }
    public void setBoardQty(BigDecimal boardQty) { this.boardQty = boardQty; }
    public BigDecimal getPickedQty() { return pickedQty; }
    public void setPickedQty(BigDecimal pickedQty) { this.pickedQty = pickedQty; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public Long getMaterialId() { return materialId; }
    public void setMaterialId(Long materialId) { this.materialId = materialId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getStorageLocationId() { return storageLocationId; }
    public void setStorageLocationId(Long storageLocationId) { this.storageLocationId = storageLocationId; }
}
