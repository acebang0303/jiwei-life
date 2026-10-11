package com.jiwei.market.controller.user;

import com.jiwei.common.result.Result;
import com.jiwei.market.dto.SeckillBuyDTO;
import com.jiwei.market.seckill.ratelimit.RateLimit;
import com.jiwei.market.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * C端秒杀购买接口（Redis Lua原子操作）
 */
@RestController
@RequestMapping("/user/seckill")
@Slf4j
@Tag(name = "秒杀购买相关接口")
public class SeckillBuyController {

    @Autowired
    private SeckillService seckillService;

    @PostMapping("/{id}/buy")
    @Operation(summary = "抢购秒杀商品")
    @RateLimit(key = "seckill:buy", permitsPerSecond = 200, intervalSeconds = 1)
    public Result<Long> buy(@PathVariable Long id, @RequestBody SeckillBuyDTO dto) {
        dto.setActivityId(id);
        log.info("秒杀抢购: activityId={}, dishId={}", id, dto.getDishId());
        Long orderId = seckillService.seckillBuy(dto);
        return Result.success(orderId);
    }
}
