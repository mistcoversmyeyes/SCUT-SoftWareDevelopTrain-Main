package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {
    @Select("SELECT * FROM inbound_order WHERE id = #{id} FOR UPDATE")
    InboundOrder selectByIdForUpdate(@Param("id") Long id);

    InboundPrintHeader selectPrintHeader(@Param("id") Long id);

    List<InboundPrintLine> selectPrintLines(@Param("id") Long id);

    List<KanbanPrintResponse> selectKanbanPrints(@Param("id") Long id);
}
