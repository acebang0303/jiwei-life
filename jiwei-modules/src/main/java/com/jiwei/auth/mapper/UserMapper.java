package com.jiwei.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 用户数据访问接口
 */
@DS("user")
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
