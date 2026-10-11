package com.jiwei.market.shortlink.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.shortlink.component.ShortCodeBloomFilter;
import com.jiwei.market.shortlink.mapper.ShortLinkMapper;
import com.jiwei.market.shortlink.util.Base62;
import com.jiwei.market.shortlink.util.MurmurHash;
import com.jiwei.pojo.entity.ShortLink;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 短链服务：MurmurHash(64) + Base62 → 短码；重定向走 Caffeine → 布隆 → DB 三级链路。
 */
@DS("market")
@Service
public class ShortLinkService {

    private static final int MAX_RETRY = 10;

    private final ShortLinkMapper mapper;
    private final ShortCodeBloomFilter bloomFilter;
    private final Cache<String, String> redirectCache = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofHours(24)).build();

    public ShortLinkService(ShortLinkMapper mapper, ShortCodeBloomFilter bloomFilter) {
        this.mapper = mapper;
        this.bloomFilter = bloomFilter;
    }

    @Transactional
    public String create(String longUrl) {
        long urlHash = MurmurHash.hash64(longUrl);
        // 修复：校验 longUrl 相等，避免哈希碰撞误判
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

    @Transactional
    public String resolve(String code) {
        String cached = redirectCache.getIfPresent(code);
        if (cached != null) {
            mapper.incrementClickCount(code);
            return cached;
        }
        if (!bloomFilter.mightContain(code)) {
            throw new OrderBusinessException("短链不存在");
        }
        ShortLink link = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, code));
        if (link == null) {
            throw new OrderBusinessException("短链不存在");
        }
        mapper.incrementClickCount(code);
        redirectCache.put(code, link.getLongUrl());
        return link.getLongUrl();
    }
}
