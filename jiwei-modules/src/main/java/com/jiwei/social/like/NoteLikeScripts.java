package com.jiwei.social.like;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 点赞链路的 Lua 脚本（原子性载体）。
 *
 * 脚本放在 resources/lua/*.lua 而不是内联在 Java 字符串里：
 * 便于 review 与用 redis-cli --eval 单独验证（本次改造就是这么验的）。
 */
@Component
public class NoteLikeScripts {

    private final DefaultRedisScript<String> toggleScript;
    private final DefaultRedisScript<String> flushScript;

    public NoteLikeScripts() {
        this.toggleScript = load("lua/note_like_toggle.lua");
        this.flushScript = load("lua/note_like_flush.lua");
    }

    private static DefaultRedisScript<String> load(String path) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(String.class);
        return script;
    }

    /** 单用户原子切换（非热点路径）。 */
    public DefaultRedisScript<String> toggle() {
        return toggleScript;
    }

    /** 按笔记批量原子刷写（热点路径 / 退出热点时的补刷）。 */
    public DefaultRedisScript<String> flush() {
        return flushScript;
    }
}
