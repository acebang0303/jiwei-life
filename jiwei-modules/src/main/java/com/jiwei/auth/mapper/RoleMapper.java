package com.jiwei.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 角色数据访问接口
 */
@DS("auth")
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
