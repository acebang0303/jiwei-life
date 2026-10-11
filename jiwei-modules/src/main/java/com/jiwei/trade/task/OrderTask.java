package com.jiwei.trade.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.market.service.SeckillService;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单定时任务
 *
 * 1. 每分钟扫描超时未支付订单（15分钟），自动取消
 * 2. 每小时扫描已完成但超过24小时未确认的订单，自动完成
 */
@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SeckillService seckillService;

    /**
     * 处理超时未支付订单
     * 每分钟执行一次，取消创建超过15分钟仍未支付的订单
     */
    @Scheduled(cron = "0 * * * * ?")
    public void processTimeoutOrder() {
        log.debug("开始处理超时未支付订单...");

        //1、查询待支付且超过15分钟的订单
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(15);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getStatus, Orders.PENDING_PAYMENT)
               .lt(Orders::getCreateTime, deadline);

        List<Orders> timeoutOrders = orderMapper.selectList(wrapper);

        //2、批量取消
        for (Orders order : timeoutOrders) {
            order.setStatus(Orders.CANCELLED);
            order.setCancelReason("支付超时，系统自动取消");
            order.setCancelTime(LocalDateTime.now());
            orderMapper.updateById(order);

            //3、回补秒杀库存（如果是秒杀订单）
            if (order.getIsSeckill() != null && order.getIsSeckill() == 1) {
                seckillService.restoreSeckillOnTimeout(order.getSeckillActivityId(), order.getUserId());
            }

            log.info("订单支付超时自动取消: orderId={}, orderNumber={}", order.getId(), order.getNumber());
        }
    }

    /**
     * 处理待取餐超过24小时的订单，自动完成
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void processDeliveredOrder() {
        log.debug("开始处理超时未完成订单...");

        LocalDateTime deadline = LocalDateTime.now().minusHours(24);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getStatus, Orders.AWAITING_PICKUP)
               .lt(Orders::getUpdateTime, deadline);

        List<Orders> deliveredOrders = orderMapper.selectList(wrapper);
        for (Orders order : deliveredOrders) {
            order.setStatus(Orders.COMPLETED);
            order.setDeliveryTime(LocalDateTime.now());
            orderMapper.updateById(order);
            log.info("订单自动完成: orderId={}", order.getId());
        }
    }
}
