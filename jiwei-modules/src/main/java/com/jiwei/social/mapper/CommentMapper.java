package com.jiwei.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("social")
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
