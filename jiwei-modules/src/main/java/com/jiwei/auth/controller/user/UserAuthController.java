package com.jiwei.auth.controller.user;

import com.jiwei.auth.dto.UserLoginDTO;
import com.jiwei.auth.service.UserAuthService;
import com.jiwei.common.constant.JwtClaimsConstant;
import com.jiwei.common.result.Result;
import com.jiwei.framework.properties.JwtProperties;
import com.jiwei.framework.utils.JwtUtil;
import com.jiwei.pojo.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * C端用户认证接口
 */
@RestController
@RequestMapping("/user/user")
@Slf4j
@Tag(name = "用户认证相关接口")
public class UserAuthController {

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 微信用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "微信用户登录")
    public Result<Map<String, Object>> login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
        log.info("微信用户登录，code: {}", userLoginDTO.getCode());

        //1、微信登录
        User user = userAuthService.wxLogin(userLoginDTO);

        //2、生成JWT令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        //3、构建返回数据
        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("openid", user.getOpenid());
        data.put("token", token);

        return Result.success(data);
    }
}
