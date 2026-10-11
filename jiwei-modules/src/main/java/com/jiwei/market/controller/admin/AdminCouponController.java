package com.jiwei.market.controller.admin;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.market.service.CouponService;
import com.jiwei.market.service.SeckillService;
import com.jiwei.pojo.entity.CouponTemplate;
import com.jiwei.pojo.entity.SeckillActivity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端优惠券接口
 */
@RestController
@RequestMapping("/admin/coupon")
@Slf4j
@Tag(name = "优惠券管理相关接口")
public class AdminCouponController {

    @Autowired
    private CouponService couponService;

    /**
     * 创建优惠券模板
     *
     * @param template
     * @return
     */
    @PostMapping("/template")
    @Operation(summary = "创建优惠券模板")
    public Result<String> createTemplate(@RequestBody CouponTemplate template) {
        log.info("创建优惠券模板: {}", template);
        couponService.createTemplate(template);
        return Result.success();
    }

    /**
     * 分页查询模板
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/template/page")
    @Operation(summary = "分页查询模板")
    public Result<PageResult> pageTemplate(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult pageResult = couponService.pageTemplate(page, pageSize);
        return Result.success(pageResult);
    }

    /**
     * 批量发放优惠券
     *
     * @param templateId
     * @param userIds
     * @return
     */
    @PostMapping("/grant")
    @Operation(summary = "批量发放优惠券")
    public Result<String> grant(@RequestParam Long templateId,
                                 @RequestParam java.util.List<Long> userIds) {
        log.info("批量发放优惠券: templateId={}, count={}", templateId, userIds.size());
        couponService.grant(templateId, userIds);
        return Result.success();
    }

    /**
     * 启用/禁用优惠券模板
     */
    @PutMapping("/template/{id}/status")
    @Operation(summary = "启用/禁用优惠券模板")
    public Result<String> updateTemplateStatus(@PathVariable Long id, @RequestParam Integer status) {
        log.info("更新优惠券模板状态: id={}, status={}", id, status);
        couponService.updateTemplateStatus(id, status);
        return Result.success();
    }
}
