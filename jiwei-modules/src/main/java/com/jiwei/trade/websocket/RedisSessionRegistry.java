package com.jiwei.trade.websocket;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 会话注册表：记录在线用户与 session 归属（跨实例共享）。
 */
@Component
public class RedisSessionRegistry {

    private static final String ONLINE_USERS_KEY = "ws:online:users";
    private static final String USER_SESSIONS_PREFIX = "ws:user:";
    private static final String SESSION_USER_PREFIX = "ws:session:";

    private final StringRedisTemplate redisTemplate;

    public RedisSessionRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void register(String sessionId, String userId) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
        redisTemplate.opsForSet().add(USER_SESSIONS_PREFIX + userId + ":sessions", sessionId);
        redisTemplate.opsForValue().set(SESSION_USER_PREFIX + sessionId, userId);
    }

    public void unregister(String sessionId) {
        String userId = redisTemplate.opsForValue().get(SESSION_USER_PREFIX + sessionId);
        if (userId == null) {
            return;
        }
        redisTemplate.delete(SESSION_USER_PREFIX + sessionId);
        String key = USER_SESSIONS_PREFIX + userId + ":sessions";
        redisTemplate.opsForSet().remove(key, sessionId);
        Long remaining = redisTemplate.opsForSet().size(key);
        if (remaining == null || remaining == 0) {
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
        }
    }
}
