package com.jiwei.market.seckill.mq;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀下单消息。
 *
 * limitPerUser / startTime / endTime 由 seckillBuy 时随消息携带：
 * 消费端与事务回查不再回查 DB 活动表，热路径每请求 0 次 DB 查询。
 * 时间窗口随消息传给预扣 Lua，原子校验防绕过。
 * 兼容旧消息（字段为 null）：limitPerUser 缺省按 1，时间为空时跳过 Lua 时间校验。
 */
public record SeckillMessage(String orderNo, Long userId, Long activityId,
                             Long dishId, int quantity, BigDecimal payAmount,
                             Integer limitPerUser, LocalDateTime startTime, LocalDateTime endTime) {
}
