package com.jiwei.auth.service;

import com.jiwei.auth.dto.EmployeeLoginDTO;
import com.jiwei.pojo.entity.Employee;

/**
 * 管理员服务接口
 */
public interface EmployeeService {

    /**
     * 管理员登录
     *
     * @param employeeLoginDTO 登录信息
     * @return 管理员实体
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 根据ID查询管理员
     */
    Employee getById(Long empId);

    /**
     * 分页查询员工
     */
    com.jiwei.common.result.PageResult pageQuery(int page, int pageSize, String name);
}
