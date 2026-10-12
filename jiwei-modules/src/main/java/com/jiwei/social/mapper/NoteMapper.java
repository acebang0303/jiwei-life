package com.jiwei.social.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@DS("social")
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /** 按净增量更新点赞计数（GREATEST 下限 0，防负数） */
    @Update("UPDATE note SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{noteId}")
    int incrLikeCount(@Param("noteId") Long noteId, @Param("delta") Integer delta);

    /** 全量读取 (id, likeCount) 作为对账的权威基线；只取两列，不拉正文 */
    @Select("SELECT id AS id, like_count AS likeCount FROM note")
    List<Map<String, Object>> selectLikeCounts();
}
