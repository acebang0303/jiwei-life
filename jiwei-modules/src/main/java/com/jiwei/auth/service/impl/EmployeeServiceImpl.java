package com.jiwei.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.auth.dto.EmployeeLoginDTO;
import com.jiwei.auth.mapper.EmployeeMapper;
import com.jiwei.auth.service.EmployeeService;
import com.jiwei.common.constant.MessageConstant;
import com.jiwei.common.constant.StatusConstant;
import com.jiwei.common.exception.AccountLockedException;
import com.jiwei.common.exception.AccountNotFoundException;
import com.jiwei.common.exception.PasswordErrorException;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Employee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 管理员服务实现类
 */
@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * 管理员登录
     *
     * @param employeeLoginDTO 登录信息
     * @return 管理员实体
     */
    @Override
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询管理员
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getUsername, username);
        Employee employee = employeeMapper.selectOne(wrapper);

        //2、账号不存在
        if (employee == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //3、密码校验（BCrypt）
        if (!passwordEncoder.matches(password, employee.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        //4、账号状态校验
        if (employee.getStatus().equals(StatusConstant.DISABLE)) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //5、返回管理员实体（不含密码）
        log.info("管理员登录成功，empId: {}", employee.getId());
        return employee;
    }

    @Override
    public Employee getById(Long empId) {
        return employeeMapper.selectById(empId);
    }

    @Override
    public PageResult pageQuery(int page, int pageSize, String name) {
        Page<Employee> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(name != null, Employee::getName, name)
               .orderByDesc(Employee::getCreateTime);
        Page<Employee> result = employeeMapper.selectPage(p, wrapper);
        return new PageResult(result.getTotal(), result.getRecords());
    }
}
