package com.jiwei.market.shortlink.component;

import com.jiwei.market.shortlink.mapper.ShortLinkMapper;
import com.jiwei.market.shortlink.util.SimpleBloomFilter;
import com.jiwei.pojo.entity.ShortLink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 短码布隆过滤器：重定向先问过滤器，「一定不存在」直接 404，防缓存穿透。
 * 启动时加载存量短码。
 */
@Component
@Slf4j
public class ShortCodeBloomFilter implements ApplicationRunner {

    private final ShortLinkMapper mapper;
    private final SimpleBloomFilter bloomFilter;

    public ShortCodeBloomFilter(ShortLinkMapper mapper) {
        this.mapper = mapper;
        this.bloomFilter = new SimpleBloomFilter(100_000, 0.001);
    }

    @Override
    public void run(ApplicationArguments args) {
        long count = 0;
        for (ShortLink link : mapper.selectList(null)) {
            bloomFilter.put(link.getShortCode());
            count++;
        }
        log.info("Bloom filter initialized with {} short codes", count);
    }

    public void add(String code) {
        bloomFilter.put(code);
    }

    public boolean mightContain(String code) {
        return bloomFilter.mightContain(code);
    }
}
