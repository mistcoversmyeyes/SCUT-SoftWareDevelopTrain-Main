package com.scut.wms.outbound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OutboundOrderMapper extends BaseMapper<OutboundOrder> {
    @Select("SELECT * FROM outbound_order WHERE id = #{id} FOR UPDATE")
    OutboundOrder selectByIdForUpdate(@Param("id") Long id);

    OutboundPrintHeader selectPrintHeader(@Param("id") Long id);

    List<OutboundPrintLine> selectPrintLines(@Param("id") Long id);
}
