package com.jiwei.auth.controller.user;

import com.jiwei.auth.service.UserAuthService;
import com.jiwei.common.constant.JwtClaimsConstant;
import com.jiwei.common.result.Result;
import com.jiwei.framework.properties.JwtProperties;
import com.jiwei.framework.utils.JwtUtil;
import com.jiwei.pojo.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 开发环境专用：本地 Mock 登录（无需真实微信 code）
 * 仅在 dev profile 下注册，生产环境不暴露
 */
@RestController
@RequestMapping("/user/user")
@Profile("dev")
@Slf4j
@Tag(name = "开发环境 Mock 登录")
public class MockLoginController {

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * Mock 登录：传 openid 直接签发 token，跳过微信 code 换 openid
     */
    @PostMapping("/mock-login")
    @Operation(summary = "本地 Mock 登录（仅 dev）")
    public Result<Map<String, Object>> mockLogin(@RequestParam String openid) {
        log.info("Mock 登录，openid: {}", openid);

        User user = userAuthService.mockLogin(openid);

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("openid", user.getOpenid());
        data.put("token", token);

        return Result.success(data);
    }
}
