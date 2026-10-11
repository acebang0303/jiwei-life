package com.jiwei.market.seckill.mq;

import java.math.BigDecimal;

/**
 * 秒杀下单消息。
 */
public record SeckillMessage(String orderNo, Long userId, Long activityId,
                             Long dishId, int quantity, BigDecimal payAmount) {
}
