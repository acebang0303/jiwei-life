package com.jiwei.market.shortlink.service;

import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.shortlink.component.ClickCountBuffer;
import com.jiwei.market.shortlink.component.ShortCodeBloomFilter;
import com.jiwei.market.shortlink.mapper.ShortLinkMapper;
import com.jiwei.pojo.entity.ShortLink;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortLinkServiceTest {

    private final ShortLinkMapper mapper = mock(ShortLinkMapper.class);
    private final ShortCodeBloomFilter bloomFilter = mock(ShortCodeBloomFilter.class);
    private final ClickCountBuffer clickBuffer = mock(ClickCountBuffer.class);

    private ShortLinkService newService() {
        return new ShortLinkService(mapper, bloomFilter, clickBuffer);
    }

    private static ShortLink link(String code, String url) {
        return ShortLink.builder().shortCode(code).longUrl(url).clickCount(0L).build();
    }

    // ---------- create: 长链协议校验（开放重定向/XSS 防护） ----------

    @Test
    void shouldRejectJavascriptScheme() {
        assertThatThrownBy(() -> newService().create("javascript:alert(1)"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void shouldRejectSchemeRelativeUrl() {
        // //evil.com 会被浏览器当作协议相对地址跳走，等价于开放重定向
        assertThatThrownBy(() -> newService().create("//evil.com/phish"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void shouldRejectDataUri() {
        assertThatThrownBy(() -> newService().create("data:text/html,<script>alert(1)</script>"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void shouldRejectControlCharacters() {
        assertThatThrownBy(() -> newService().create("https://a.com/\r\nSet-Cookie:x=1"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void shouldRejectBlankAndOverLongUrl() {
        assertThatThrownBy(() -> newService().create("   ")).isInstanceOf(OrderBusinessException.class);
        assertThatThrownBy(() -> newService().create("https://a.com/" + "x".repeat(2100)))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void shouldRejectHostlessUrl() {
        assertThatThrownBy(() -> newService().create("https:///no-host"))
                .isInstanceOf(OrderBusinessException.class);
    }

    @Test
    void shouldAcceptPlainHttpsUrl() {
        when(mapper.selectOne(any())).thenReturn(null);
        when(mapper.selectCount(any())).thenReturn(0L);

        String code = newService().create("https://a.com/path?q=1");

        assertThat(code).isNotBlank();
        verify(mapper).insert(any(ShortLink.class));
        verify(bloomFilter).add(code);
    }

    // ---------- create: 幂等 ----------

    @Test
    void shouldReturnExistingCodeWhenUrlAlreadyStored() {
        when(mapper.selectOne(any())).thenReturn(link("abc123", "https://a.com/x"));

        assertThat(newService().create("https://a.com/x")).isEqualTo("abc123");
        verify(mapper, never()).insert(any(ShortLink.class));
    }

    // ---------- create: 并发同名长链回查（原实现在事务内吞异常会 rollback-only） ----------

    @Test
    void shouldRecoverFromDuplicateKeyByReSelecting() {
        ShortLink raced = link("raced1", "https://a.com/race");
        // 第一次 selectOne（预查）返回 null → insert 抛唯一键冲突 → 回查拿到竞争者的记录
        when(mapper.selectOne(any())).thenReturn(null, raced);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(ShortLink.class))).thenThrow(new DuplicateKeyException("uk_short_code"));

        assertThat(newService().create("https://a.com/race")).isEqualTo("raced1");
    }

    // ---------- resolve: 本地缓存命中不应触达 DB ----------

    @Test
    void shouldServeSecondResolveFromLocalCacheWithoutHittingDatabase() {
        when(bloomFilter.mightContain(anyString())).thenReturn(true);
        when(mapper.selectOne(any())).thenReturn(link("c1", "https://a.com/1"));
        ShortLinkService service = newService();

        assertThat(service.resolve("c1")).isEqualTo("https://a.com/1");
        assertThat(service.resolve("c1")).isEqualTo("https://a.com/1");

        // 只有第一次（缓存未命中）查库；命中后点击计数只走内存攒批
        verify(mapper, times(1)).selectOne(any());
        verify(clickBuffer, times(2)).increment("c1");
    }

    // ---------- resolve: 布隆过滤器防穿透 ----------

    @Test
    void shouldRejectUnknownCodeBeforeTouchingDatabase() {
        when(bloomFilter.mightContain(anyString())).thenReturn(false);

        assertThatThrownBy(() -> newService().resolve("nope"))
                .isInstanceOf(OrderBusinessException.class);
        verify(mapper, never()).selectOne(any());
    }
}
