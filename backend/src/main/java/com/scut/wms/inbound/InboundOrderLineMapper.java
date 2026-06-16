package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InboundOrderLineMapper extends BaseMapper<InboundOrderLine> {
    @Select("SELECT * FROM inbound_order_line WHERE id = #{id} FOR UPDATE")
    InboundOrderLine selectByIdForUpdate(@Param("id") Long id);
}
