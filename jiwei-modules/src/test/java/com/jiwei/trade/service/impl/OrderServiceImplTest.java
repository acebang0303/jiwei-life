package com.jiwei.trade.service.impl;

import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.service.CouponService;
import com.jiwei.market.service.SeckillService;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final SeckillService seckillService = mock(SeckillService.class);
    private final CouponService couponService = mock(CouponService.class);

    private OrderServiceImpl newService() {
        OrderServiceImpl svc = new OrderServiceImpl();
        ReflectionTestUtils.setField(svc, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(svc, "seckillService", seckillService);
        ReflectionTestUtils.setField(svc, "couponService", couponService);
        return svc;
    }

    @Test
    void timeoutShouldNotCancelAlreadyPaidOrder() {
        // CAS 返回 0 = 状态已变更（已支付），应跳过取消和回补
        when(orderMapper.cancelPendingIfUnpaid(any())).thenReturn(0);
        OrderServiceImpl svc = newService();

        svc.handleTimeoutOrder(100L);

        verify(orderMapper).cancelPendingIfUnpaid(100L);
        verify(seckillService, never()).restoreSeckillOnTimeout(any(), any());
        verify(couponService, never()).release(any());
    }

    @Test
    void timeoutShouldCancelAndRestockWhenStillPending() {
        // CAS 返回 1 = 成功取消，需回补秒杀库存
        Orders order = new Orders();
        order.setId(100L);
        order.setIsSeckill(1);
        order.setSeckillActivityId(5L);
        order.setUserId(404L);
        when(orderMapper.cancelPendingIfUnpaid(100L)).thenReturn(1);
        when(orderMapper.selectById(100L)).thenReturn(order);
        OrderServiceImpl svc = newService();

        svc.handleTimeoutOrder(100L);

        verify(seckillService).restoreSeckillOnTimeout(5L, 404L);
    }

    @Test
    void cancelSeckillOrderRestocksInventory() {
        // 用户主动取消待支付的秒杀单：CAS 成功后需回补 DB 库存 + Redis 库存 + 限购计数
        Orders order = new Orders();
        order.setId(100L);
        order.setUserId(404L);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setIsSeckill(1);
        order.setSeckillActivityId(5L);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(orderMapper.cancelByUser(eq(100L), anyString())).thenReturn(1);
        OrderServiceImpl svc = newService();

        svc.cancel(100L, 404L);

        verify(orderMapper).cancelByUser(100L, "用户主动取消");
        verify(seckillService).restoreSeckillOnTimeout(5L, 404L);
    }

    @Test
    void cancelNonSeckillOrderDoesNotRestock() {
        // 普通订单主动取消无需回补库存
        Orders order = new Orders();
        order.setId(101L);
        order.setUserId(404L);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setIsSeckill(0);
        when(orderMapper.selectById(101L)).thenReturn(order);
        when(orderMapper.cancelByUser(eq(101L), anyString())).thenReturn(1);
        OrderServiceImpl svc = newService();

        svc.cancel(101L, 404L);

        verify(orderMapper).cancelByUser(101L, "用户主动取消");
        verify(seckillService, never()).restoreSeckillOnTimeout(any(), any());
    }

    @Test
    void cancelNonPendingThrowsAndDoesNotRestock() {
        // 已接单（非待支付）订单不能取消，也不回补
        Orders order = new Orders();
        order.setId(102L);
        order.setUserId(404L);
        order.setStatus(Orders.TO_BE_CONFIRMED);
        order.setIsSeckill(1);
        order.setSeckillActivityId(5L);
        when(orderMapper.selectById(102L)).thenReturn(order);
        OrderServiceImpl svc = newService();

        assertThrows(OrderBusinessException.class, () -> svc.cancel(102L, 404L));

        verify(orderMapper, never()).cancelByUser(any(), any());
        verify(seckillService, never()).restoreSeckillOnTimeout(any(), any());
    }
}
