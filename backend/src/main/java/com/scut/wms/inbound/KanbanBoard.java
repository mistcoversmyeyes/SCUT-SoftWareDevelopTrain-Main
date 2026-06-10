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
    private BigDecimal boardQty;
    private String status;
    private LocalDateTime printedAt;
    private LocalDateTime receivedAt;
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

    public BigDecimal getBoardQty() {
        return boardQty;
    }

    public void setBoardQty(BigDecimal boardQty) {
        this.boardQty = boardQty;
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
