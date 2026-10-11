package com.jiwei.market.seckill.mq;

import com.jiwei.market.service.SeckillService;
import com.jiwei.trade.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class SeckillOrderCreateConsumerTest {

    private final SeckillService seckillService = mock(SeckillService.class);
    private final OrderService orderService = mock(OrderService.class);

    private SeckillOrderCreateConsumer newConsumer() {
        SeckillOrderCreateConsumer consumer = new SeckillOrderCreateConsumer(seckillService, orderService);
        // nameServer/consumerGroup 用于 @PostConstruct，单测不触发 start()
        ReflectionTestUtils.setField(consumer, "nameServer", "localhost:9876");
        ReflectionTestUtils.setField(consumer, "consumerGroup", "test-group");
        return consumer;
    }

    private SeckillMessage msg(String orderNo) {
        return new SeckillMessage(orderNo, 404L, 5L, 5L, 1, new BigDecimal("9.90"));
    }

    @Test
    void duplicateDeliveryShouldSkipStockDeduction() {
        when(orderService.seckillOrderExists("order-no-1")).thenReturn(true);
        SeckillOrderCreateConsumer consumer = newConsumer();

        long result = consumer.handleMessage(msg("order-no-1"));

        assertThat(result).isEqualTo(0L);
        verify(seckillService, never()).deductStock(any(), anyInt());
        verify(orderService, never()).createSeckillOrder(any());
    }

    @Test
    void freshDeliveryDeductsThenCreatesOrder() {
        when(orderService.seckillOrderExists("order-no-2")).thenReturn(false);
        when(seckillService.deductStock(5L, 1)).thenReturn(true);
        SeckillOrderCreateConsumer consumer = newConsumer();

        long result = consumer.handleMessage(msg("order-no-2"));

        assertThat(result).isEqualTo(1L);
        verify(seckillService).deductStock(5L, 1);
        verify(orderService).createSeckillOrder(any());
    }

    @Test
    void stockShortageRollsBackRedisAndReturnsMinusOne() {
        when(orderService.seckillOrderExists("order-no-3")).thenReturn(false);
        when(seckillService.deductStock(5L, 1)).thenReturn(false);
        SeckillOrderCreateConsumer consumer = newConsumer();

        long result = consumer.handleMessage(msg("order-no-3"));

        assertThat(result).isEqualTo(-1L);
        verify(seckillService).revertRedisStock(5L, 5L, 404L, 1);
        verify(orderService, never()).createSeckillOrder(any());
    }
}
