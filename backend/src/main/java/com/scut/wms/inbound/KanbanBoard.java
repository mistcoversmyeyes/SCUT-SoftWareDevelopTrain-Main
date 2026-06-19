package com.scut.wms.inbound;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("kanban_board")
public class KanbanBoard {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String kanbanCode;
    private Long inboundOrderId;
    private Long inboundOrderLineId;
    private Long locationId = 0L;
    private Long containerTypeId = 0L;
    private BigDecimal boardQty;
    private BigDecimal pickedQty;
    private String status;
    private LocalDateTime printedAt;
    private LocalDateTime receivedAt;
    private Long lockedByOrderId;
    private Long lockedByOrderLineId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKanbanCode() {
        return kanbanCode;
    }

    public void setKanbanCode(String kanbanCode) {
        this.kanbanCode = kanbanCode;
    }

    public Long getInboundOrderId() {
        return inboundOrderId;
    }

    public void setInboundOrderId(Long inboundOrderId) {
        this.inboundOrderId = inboundOrderId;
    }

    public Long getInboundOrderLineId() {
        return inboundOrderLineId;
    }

    public void setInboundOrderLineId(Long inboundOrderLineId) {
        this.inboundOrderLineId = inboundOrderLineId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getContainerTypeId() {
        return containerTypeId;
    }

    public void setContainerTypeId(Long containerTypeId) {
        this.containerTypeId = containerTypeId;
    }

    public BigDecimal getBoardQty() {
        return boardQty;
    }

    public void setBoardQty(BigDecimal boardQty) {
        this.boardQty = boardQty;
    }

    public BigDecimal getPickedQty() {
        return pickedQty;
    }

    public void setPickedQty(BigDecimal pickedQty) {
        this.pickedQty = pickedQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getPrintedAt() {
        return printedAt;
    }

    public void setPrintedAt(LocalDateTime printedAt) {
        this.printedAt = printedAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Long getLockedByOrderId() {
        return lockedByOrderId;
    }

    public void setLockedByOrderId(Long lockedByOrderId) {
        this.lockedByOrderId = lockedByOrderId;
    }

    public Long getLockedByOrderLineId() {
        return lockedByOrderLineId;
    }

    public void setLockedByOrderLineId(Long lockedByOrderLineId) {
        this.lockedByOrderLineId = lockedByOrderLineId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
