package com.jiwei.auth.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.auth.dto.UserLoginDTO;
import com.jiwei.auth.mapper.UserMapper;
import com.jiwei.auth.service.UserAuthService;
import com.jiwei.common.constant.StatusConstant;
import com.jiwei.framework.properties.WeChatProperties;
import com.jiwei.framework.utils.WeChatPayUtil;
import com.jiwei.pojo.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户认证服务实现类
 */
@Service
@Slf4j
public class UserAuthServiceImpl implements UserAuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 微信用户登录
     *
     * @param userLoginDTO 登录请求（包含code）
     * @return 用户实体
     */
    @Override
    public User wxLogin(UserLoginDTO userLoginDTO) {
        //1、调用微信接口获取openid
        // 如果小程序 AppID/AppSecret 未配置，WeChatPayUtil 内部自动降级为 Mock 模式
        // Mock 模式：直接返回 code 作为 openid（适合开发调试）
        String openid = weChatPayUtil.getOpenid(userLoginDTO.getCode());

        return findOrRegister(openid);
    }

    /**
     * 根据ID查询用户信息
     *
     * @param userId 用户ID
     * @return 用户实体
     */
    @Override
    public User getUserById(Long userId) {
        //1、查询用户信息
        return userMapper.selectById(userId);
    }

    @Override
    public User mockLogin(String openid) {
        return findOrRegister(openid);
    }

    /**
     * 按 openid 查找用户，不存在则自动注册
     */
    private User findOrRegister(String openid) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getOpenid, openid);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .status(StatusConstant.ENABLE)
                    .growthValue(0)
                    .level(1)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            userMapper.insert(user);
            log.info("新用户注册成功，userId: {}, openid: {}", user.getId(), openid);
        } else {
            log.info("老用户登录，userId: {}", user.getId());
        }

        return user;
    }

    /**
     * 更新用户信息
     *
     * @param user 用户实体
     */
    @Override
    public void updateUser(User user) {
        //1、更新用户信息
        userMapper.updateById(user);
        log.info("用户信息更新成功，userId: {}", user.getId());
    }
}
