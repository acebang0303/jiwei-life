package com.jiwei.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.NoteLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.List;

@DS("social")
@Mapper
public interface NoteLikeMapper extends BaseMapper<NoteLike> {

    /** 批量幂等插入点赞明细（依赖 uk_note_user 唯一键，重复赞被忽略） */
    @Insert("<script>" +
            "INSERT IGNORE INTO note_like(note_id, user_id, create_time) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.noteId}, #{r.userId}, NOW())" +
            "</foreach>" +
            "</script>")
    int insertIgnoreBatch(@Param("list") List<NoteLike> list);

    /** 批量幂等删除点赞明细（取消赞，删不存在也无副作用） */
    @Delete("<script>" +
            "DELETE FROM note_like WHERE (note_id, user_id) IN " +
            "<foreach collection='list' item='r' open='(' separator=',' close=')'>" +
            "(#{r.noteId}, #{r.userId})" +
            "</foreach>" +
            "</script>")
    int deleteBatch(@Param("list") List<NoteLike> list);
}
