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
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * C端用户JWT令牌拦截器
 * 验证请求头中的 Authorization，查黑名单，解析后设置 ThreadLocal
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";

    /** 游客可浏览的公开接口（有 token 仍解析 userId，无 token 直接放行） */
    private static final List<String> PUBLIC_USER_PATTERNS = List.of(
            "/user/merchant/list",
            "/user/merchant/{id}",
            "/user/merchant/{id}/dishes",
            "/user/category/list",
            "/user/dish/list",
            "/user/setmeal/list",
            "/user/dish/search",
            "/user/note/feed",
            "/user/note/hot",
            "/user/note/{id}",
            "/user/seckill/list",
            "/user/activity/list"
    );

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    /**
     * 公开浏览接口：未登录放行（游客可看内容），有 token 则解析 userId 提供个性化。
     * 注意：AntPathMatcher 不支持 {id} 占位符，必须用 PathPattern（Spring Web 的 URI 模板）。
     */
    private boolean isPublicPath(String uri) {
        PathContainer path = PathContainer.parsePath(uri);
        return PUBLIC_USER_PATTERNS.stream().anyMatch(p -> {
            PathPattern pattern = PATH_PATTERN_PARSER.parse(p);
            return pattern.matches(path);
        });
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        //1.5、公开浏览接口：无 token 直接放行（游客浏览），有 token 走下面的解析
        if (isPublicPath(request.getRequestURI()) && (token == null || token.isEmpty())) {
            return true;
        }

        //2、校验令牌是否为空
        if (token == null || token.isEmpty()) {
            log.warn("用户令牌为空，请求URI: {}", request.getRequestURI());
            response.setStatus(401);
            return false;
        }

        try {
            //3、解析JWT获取tokenID
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            String jti = claims.getId();

            //4、校验token是否在黑名单中（已退出登录）
            if (Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + jti))) {
                log.warn("用户令牌已被加入黑名单，jti: {}", jti);
                response.setStatus(401);
                return false;
            }

            //5、获取用户ID存入ThreadLocal
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            BaseContext.setCurrentId(userId);

            log.debug("用户认证通过，userId: {}", userId);
            return true;
        } catch (Exception e) {
            log.warn("用户令牌校验失败: {}", e.getMessage());
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
