package com.jiwei.market.couponshare.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.market.couponshare.service.CouponShareService;
import com.jiwei.market.mapper.CouponTemplateMapper;
import com.jiwei.market.shortlink.service.ShortLinkService;
import com.jiwei.pojo.entity.CouponTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@DS("market")
@Service
@Slf4j
public class CouponShareServiceImpl implements CouponShareService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final ShortLinkService shortLinkService;

    /** H5 落地页基础地址，dev=http://localhost:5173，生产用真域名覆盖 */
    @Value("${coupon.share.base-url:http://localhost:5173}")
    private String baseUrl;

    public CouponShareServiceImpl(CouponTemplateMapper couponTemplateMapper,
                                  ShortLinkService shortLinkService) {
        this.couponTemplateMapper = couponTemplateMapper;
        this.shortLinkService = shortLinkService;
    }

    @Override
    public String createShareLink(Long templateId) {
        String longUrl = baseUrl + "/coupon-share?templateId=" + templateId;
        return shortLinkService.create(longUrl);
    }

    @Override
    public CouponTemplate getShareInfo(Long templateId) {
        return couponTemplateMapper.selectById(templateId);
    }

    @Override
    public String generateMiniCode(Long templateId) {
        // 开发占位：真实实现应调微信 getwxacodeunlimit 返回小程序码图
        String text = "coupon-template-" + templateId;
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='200' height='200'>"
                + "<rect width='200' height='200' fill='#FF7A3D'/>"
                + "<text x='100' y='105' font-size='14' fill='white' text-anchor='middle'>"
                + text + "</text></svg>";
        return "data:image/svg+xml;base64,"
                + java.util.Base64.getEncoder().encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
