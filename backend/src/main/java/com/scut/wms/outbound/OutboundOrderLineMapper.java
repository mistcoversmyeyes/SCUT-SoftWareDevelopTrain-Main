package com.scut.wms.outbound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OutboundOrderLineMapper extends BaseMapper<OutboundOrderLine> {
    @Select("SELECT * FROM outbound_order_line WHERE id = #{id} FOR UPDATE")
    OutboundOrderLine selectByIdForUpdate(@Param("id") Long id);
}
