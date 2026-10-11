package com.jiwei.auth.service;

import com.jiwei.auth.dto.UserLoginDTO;
import com.jiwei.pojo.entity.User;

/**
 * 用户认证服务接口
 */
public interface UserAuthService {

    /**
     * 微信用户登录（code换openid，首次登录自动注册）
     *
     * @param userLoginDTO 登录请求
     * @return 用户实体
     */
    User wxLogin(UserLoginDTO userLoginDTO);

    /**
     * 根据ID查询用户信息
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    User getUserById(Long userId);

    /**
     * 开发环境 Mock 登录：直接按 openid 查找或注册用户，跳过微信 code 换 openid
     *
     * @param openid 微信 openid
     * @return 用户实体
     */
    User mockLogin(String openid);

    /**
     * 更新用户信息
     *
     * @param user 用户实体
     */
    void updateUser(User user);
}
