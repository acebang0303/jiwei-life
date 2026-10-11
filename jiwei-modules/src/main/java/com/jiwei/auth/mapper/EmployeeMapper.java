package com.jiwei.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.dynamic.datasource.annotation.DS;

/**
 * 管理员数据访问接口
 */
@DS("auth")
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
