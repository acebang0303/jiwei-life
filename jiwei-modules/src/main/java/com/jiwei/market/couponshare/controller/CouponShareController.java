package com.jiwei.market.couponshare.controller;

import com.jiwei.common.exception.BaseException;
import com.jiwei.common.result.Result;
import com.jiwei.market.couponshare.service.CouponShareService;
import com.jiwei.pojo.entity.CouponTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/user/coupon-share")
@Slf4j
@Tag(name = "优惠券分享短链")
public class CouponShareController {

    private final CouponShareService couponShareService;

    public CouponShareController(CouponShareService couponShareService) {
        this.couponShareService = couponShareService;
    }

    @PostMapping("/link")
    @Operation(summary = "生成分享短链")
    public Result<Map<String, String>> link(@RequestParam Long templateId) {
        String code = couponShareService.createShareLink(templateId);
        Map<String, String> data = new HashMap<>();
        data.put("shortCode", code);
        data.put("shortUrl", "/s/" + code);
        return Result.success(data);
    }

    @GetMapping("/info")
    @Operation(summary = "券信息（H5 公开展示）")
    public Result<CouponTemplate> info(@RequestParam Long templateId) {
        CouponTemplate template = couponShareService.getShareInfo(templateId);
        if (template == null) {
            throw new BaseException("优惠券不存在");
        }
        if (template.getStatus() != null && template.getStatus() == 0) {
            throw new BaseException("该优惠券活动已结束");
        }
        return Result.success(template);
    }

    @GetMapping("/minicode")
    @Operation(summary = "小程序码（开发占位）")
    public Result<Map<String, String>> minicode(@RequestParam Long templateId) {
        String dataUrl = couponShareService.generateMiniCode(templateId);
        Map<String, String> data = new HashMap<>();
        data.put("image", dataUrl);
        return Result.success(data);
    }
}
