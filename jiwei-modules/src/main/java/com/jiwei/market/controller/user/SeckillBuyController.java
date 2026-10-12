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
    // 2000 QPS：热路径已 0 次 DB（元数据三级缓存 + Lua 预扣 + 事务消息异步落库），
    // 200 QPS 会先于真实容量截断流量，压测无法探明系统水位
    @RateLimit(key = "seckill:buy", permits = 2000, intervalSeconds = 1)
    public Result<Long> buy(@PathVariable Long id, @RequestBody SeckillBuyDTO dto) {
        dto.setActivityId(id);
        log.info("秒杀抢购: activityId={}, dishId={}", id, dto.getDishId());
        Long orderId = seckillService.seckillBuy(dto);
        return Result.success(orderId);
    }
}
