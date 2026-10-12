package com.jiwei.market.shortlink.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.shortlink.component.ClickCountBuffer;
import com.jiwei.market.shortlink.component.ShortCodeBloomFilter;
import com.jiwei.market.shortlink.mapper.ShortLinkMapper;
import com.jiwei.market.shortlink.util.Base62;
import com.jiwei.market.shortlink.util.MurmurHash;
import com.jiwei.pojo.entity.ShortLink;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * 短链服务：MurmurHash(64) + Base62 → 短码；重定向走 Caffeine → 布隆 → DB 三级链路。
 *
 * 事务边界说明（刻意不加 @Transactional）：
 *   1. create 只有一条 INSERT，且并发相同长链时靠 short_code 唯一键 + 捕获
 *      DuplicateKeyException 回查实现幂等。若把它包在事务里，"捕获异常后正常返回"
 *      会让事务被标记 rollback-only，提交时抛 UnexpectedRollbackException —— 正是
 *      并发下最常走的这条路径。单条写不需要事务，交给 DB 唯一约束兜底。
 *   2. resolve 是读路径，缓存命中时不应该开事务/占连接；点击计数交由
 *      ClickCountBuffer 本地攒批，重定向热路径不再触达 DB 写。
 */
@DS("market")
@Service
public class ShortLinkService {

    private static final int MAX_RETRY = 10;
    private static final int MAX_LONG_URL_LENGTH = 2000;
    private static final String SCHEME_HTTP = "http://";
    private static final String SCHEME_HTTPS = "https://";

    private final ShortLinkMapper mapper;
    private final ShortCodeBloomFilter bloomFilter;
    private final ClickCountBuffer clickCountBuffer;

    private final Cache<String, String> redirectCache = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofHours(24)).build();

    public ShortLinkService(ShortLinkMapper mapper,
                            ShortCodeBloomFilter bloomFilter,
                            ClickCountBuffer clickCountBuffer) {
        this.mapper = mapper;
        this.bloomFilter = bloomFilter;
        this.clickCountBuffer = clickCountBuffer;
    }

    public String create(String longUrl) {
        validateLongUrl(longUrl);
        long urlHash = MurmurHash.hash64(longUrl);
        // 校验 longUrl 相等，避免哈希碰撞误判为同一条
        ShortLink existing = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getUrlHash, urlHash)
                .eq(ShortLink::getLongUrl, longUrl));
        if (existing != null) {
            return existing.getShortCode();
        }

        for (int i = 0; i < MAX_RETRY; i++) {
            String seed = i == 0 ? longUrl : longUrl + "#" + i;
            String code = Base62.encode(MurmurHash.hash64(seed));
            if (mapper.selectCount(new LambdaQueryWrapper<ShortLink>()
                    .eq(ShortLink::getShortCode, code)) > 0) {
                continue;
            }
            ShortLink link = ShortLink.builder()
                    .shortCode(code).longUrl(longUrl).urlHash(urlHash).clickCount(0L).build();
            try {
                mapper.insert(link);
                bloomFilter.add(code);
                redirectCache.put(code, longUrl);
                return code;
            } catch (DuplicateKeyException e) {
                // 并发下同链已被插入：查回已有记录返回（幂等）
                ShortLink raced = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getUrlHash, urlHash).eq(ShortLink::getLongUrl, longUrl));
                if (raced != null) {
                    return raced.getShortCode();
                }
            }
        }
        throw new OrderBusinessException("短码生成失败，请重试");
    }

    public String resolve(String code) {
        String cached = redirectCache.getIfPresent(code);
        if (cached != null) {
            clickCountBuffer.increment(code);
            return cached;
        }
        if (!bloomFilter.mightContain(code)) {
            // 布隆说「一定不存在」：直接 404，挡住缓存穿透
            throw new OrderBusinessException("短链不存在");
        }
        ShortLink link = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, code));
        if (link == null) {
            throw new OrderBusinessException("短链不存在");
        }
        redirectCache.put(code, link.getLongUrl());
        clickCountBuffer.increment(code);
        return link.getLongUrl();
    }

    /**
     * 长链合法性校验：只放行 http/https。
     * 不校验的话，create 接口可被用来签发指向 {@code javascript:} / {@code data:} /
     * 协议相对地址 {@code //evil.com} 的短链，重定向接口就成了开放重定向与 XSS 的跳板。
     */
    private void validateLongUrl(String longUrl) {
        if (!StringUtils.hasText(longUrl)) {
            throw new OrderBusinessException("长链不能为空");
        }
        String trimmed = longUrl.trim();
        if (trimmed.length() > MAX_LONG_URL_LENGTH) {
            throw new OrderBusinessException("长链过长（上限 " + MAX_LONG_URL_LENGTH + " 字符）");
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new OrderBusinessException("长链包含非法控制字符");
            }
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith(SCHEME_HTTP) && !lower.startsWith(SCHEME_HTTPS)) {
            throw new OrderBusinessException("仅支持 http/https 协议的长链");
        }
        try {
            URI uri = URI.create(trimmed);
            if (!StringUtils.hasText(uri.getHost())) {
                throw new OrderBusinessException("长链缺少合法主机名");
            }
        } catch (IllegalArgumentException e) {
            throw new OrderBusinessException("长链格式非法");
        }
    }
}
