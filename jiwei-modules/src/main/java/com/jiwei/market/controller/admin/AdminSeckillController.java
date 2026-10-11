package com.jiwei.market.controller.admin;

import com.jiwei.common.result.PageResult;
import com.jiwei.common.result.Result;
import com.jiwei.market.service.SeckillService;
import com.jiwei.pojo.entity.SeckillActivity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端秒杀接口
 */
@RestController
@RequestMapping("/admin/seckill")
@Slf4j
@Tag(name = "秒杀管理相关接口")
public class AdminSeckillController {

    @Autowired
    private SeckillService seckillService;

    /**
     * 创建秒杀活动
     *
     * @param activity
     * @return
     */
    @PostMapping
    @Operation(summary = "创建秒杀活动")
    public Result<String> create(@RequestBody SeckillActivity activity) {
        log.info("创建秒杀活动: {}", activity);
        seckillService.createActivity(activity);
        return Result.success();
    }

    /**
     * 分页查询秒杀活动
     *
     * @param page
     * @param pageSize
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询秒杀活动")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult pageResult = seckillService.pageActivity(page, pageSize);
        return Result.success(pageResult);
    }
}
