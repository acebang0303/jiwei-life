package com.jiwei.framework.interceptor;

import com.jiwei.common.constant.JwtClaimsConstant;
import com.jiwei.common.context.BaseContext;
import com.jiwei.framework.properties.JwtProperties;
import com.jiwei.framework.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员JWT令牌拦截器
 * 验证请求头中的 token，查黑名单，解析后设置 ThreadLocal
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getAdminTokenName());

        //2、校验令牌是否为空
        if (token == null || token.isEmpty()) {
            log.warn("管理员令牌为空，请求URI: {}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }

        try {
            //3、解析JWT获取tokenID
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            String jti = claims.getId();

            //4、校验token是否在黑名单中（已退出登录）
            if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jti))) {
                log.warn("管理员令牌已被加入黑名单，jti: {}", jti);
                response.setStatus(401);
                return false;
            }

            //5、获取管理员ID存入ThreadLocal
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            BaseContext.setCurrentId(empId);

            log.debug("管理员认证通过，empId: {}", empId);
            return true;
        } catch (Exception e) {
            log.warn("管理员令牌校验失败: {}", e.getMessage());
            response.setStatus(401);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        //清除ThreadLocal，防止内存泄漏
        BaseContext.removeCurrentId();
    }
}
