package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryTagMapper extends BaseMapper<InventoryTag> {
    @Select("SELECT * FROM inventory_tag WHERE id = #{id} FOR UPDATE")
    InventoryTag selectByIdForUpdate(@Param("id") Long id);
}
