package com.scut.wms.inbound;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KanbanBoardMapper extends BaseMapper<KanbanBoard> {
    @Select("SELECT * FROM kanban_board WHERE id = #{id} FOR UPDATE")
    KanbanBoard selectByIdForUpdate(@Param("id") Long id);
}
