package com.jiwei.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("social")
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /** 按净增量更新点赞计数（GREATEST 下限 0，防负数） */
    @Update("UPDATE note SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{noteId}")
    int incrLikeCount(@Param("noteId") Long noteId, @Param("delta") Integer delta);

    /** 对账整刷：直接覆盖为 Redis 权威计数（值不等才更新，避免无谓写） */
    @Update("UPDATE note SET like_count = GREATEST(0, #{count}) " +
            "WHERE id = #{noteId} AND like_count <> GREATEST(0, #{count})")
    int reconcileLikeCount(@Param("noteId") Long noteId, @Param("count") Integer count);
}
