package com.jiwei.market.controller.user;

import com.jiwei.common.result.Result;
import com.jiwei.market.service.SeckillService;
import com.jiwei.market.service.SignService;
import com.jiwei.pojo.entity.SeckillActivity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C端秒杀与签到接口
 */
@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户端秒杀签到相关接口")
public class UserSeckillController {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SignService signService;

    /**
     * 查询进行中的秒杀活动
     *
     * @return
     */
    @GetMapping("/seckill/list")
    @Operation(summary = "查询进行中的秒杀活动")
    public Result<List<SeckillActivity>> seckillList() {
        List<SeckillActivity> list = seckillService.listRunning();
        return Result.success(list);
    }

    /**
     * 秒杀活动详情
     *
     * @param id
     * @return
     */
    @GetMapping("/seckill/{id}")
    @Operation(summary = "秒杀活动详情")
    public Result<SeckillActivity> seckillDetail(@PathVariable Long id) {
        SeckillActivity activity = seckillService.getActivityById(id);
        return Result.success(activity);
    }

    /**
     * 每日签到
     *
     * @return
     */
    @PostMapping("/sign")
    @Operation(summary = "每日签到")
    public Result<String> sign() {
        Long userId = com.jiwei.common.context.BaseContext.getCurrentId();
        signService.sign(userId);
        return Result.success();
    }

    /**
     * 查询今日是否已签到
     *
     * @return
     */
    @GetMapping("/sign/today")
    @Operation(summary = "查询今日是否已签到")
    public Result<Map<String, Boolean>> signToday() {
        Long userId = com.jiwei.common.context.BaseContext.getCurrentId();
        Map<String, Boolean> result = new HashMap<>();
        result.put("signed", signService.isSignedToday(userId));
        return Result.success(result);
    }

    /**
     * 查询本月签到详情
     *
     * @return
     */
    @GetMapping("/sign/month")
    @Operation(summary = "查询本月签到详情")
    public Result<Map<String, Object>> signMonth() {
        Long userId = com.jiwei.common.context.BaseContext.getCurrentId();
        Map<String, Object> result = new HashMap<>();
        result.put("count", signService.getMonthSignCount(userId));
        result.put("streak", signService.getContinuousSignDays(userId));
        return Result.success(result);
    }
}
