package com.scut.wms.lock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("inventory_hold")
public class InventoryHold {
    public static final String ACTIVE = "ACTIVE";
    public static final String RELEASED = "RELEASED";
    public static final String SEALED = "SEALED";
    public static final String MANUAL_LOCK = "MANUAL_LOCK";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kanbanBoardId;
    private String holdType;
    private BigDecimal holdQty;
    private String status;
    private String reason;
    private String remark;
    private String operatorName;
    private String releasedReason;
    private String releasedRemark;
    private String releasedBy;
    private LocalDateTime createdAt;
    private LocalDateTime releasedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKanbanBoardId() {
        return kanbanBoardId;
    }

    public void setKanbanBoardId(Long kanbanBoardId) {
        this.kanbanBoardId = kanbanBoardId;
    }

    public String getHoldType() {
        return holdType;
    }

    public void setHoldType(String holdType) {
        this.holdType = holdType;
    }

    public BigDecimal getHoldQty() {
        return holdQty;
    }

    public void setHoldQty(BigDecimal holdQty) {
        this.holdQty = holdQty;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getReleasedReason() {
        return releasedReason;
    }

    public void setReleasedReason(String releasedReason) {
        this.releasedReason = releasedReason;
    }

    public String getReleasedRemark() {
        return releasedRemark;
    }

    public void setReleasedRemark(String releasedRemark) {
        this.releasedRemark = releasedRemark;
    }

    public String getReleasedBy() {
        return releasedBy;
    }

    public void setReleasedBy(String releasedBy) {
        this.releasedBy = releasedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }
}
