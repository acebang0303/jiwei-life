package com.jiwei.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 权限数据访问接口
 */
@DS("auth")
@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
