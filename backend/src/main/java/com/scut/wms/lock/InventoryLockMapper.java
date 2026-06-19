package com.scut.wms.lock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryLockMapper extends BaseMapper<InventoryLock> {

    List<LockOrderSummary> selectLockOrderSummaries(
            @Param("outboundNo") String outboundNo,
            @Param("materialCode") String materialCode,
            @Param("status") String status
    );

    List<LockDetailView> selectLockDetails(@Param("outboundOrderId") Long outboundOrderId);

    List<ForceLogView> selectForceLogs(@Param("outboundNo") String outboundNo);

    List<KanbanLockView> selectKanbanLocks(
            @Param("status") String status,
            @Param("materialCode") String materialCode,
            @Param("outboundNo") String outboundNo
    );

    InventoryLock selectByKanbanForUpdate(@Param("kanbanBoardId") Long kanbanBoardId);

    List<InventoryLock> selectByOrderLineForUpdate(
            @Param("outboundOrderId") Long outboundOrderId,
            @Param("outboundOrderLineId") Long outboundOrderLineId
    );
}
