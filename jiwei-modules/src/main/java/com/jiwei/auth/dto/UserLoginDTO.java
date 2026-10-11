package com.jiwei.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 微信用户登录请求DTO
 */
@Data
public class UserLoginDTO implements Serializable {
    //微信登录code
    @NotBlank(message = "微信登录code不能为空")
    private String code;
}
