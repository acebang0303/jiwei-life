package com.jiwei.auth.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员登录请求DTO
 */
@Data
public class EmployeeLoginDTO implements Serializable {
    //用户名
    private String username;
    //密码
    private String password;
}
