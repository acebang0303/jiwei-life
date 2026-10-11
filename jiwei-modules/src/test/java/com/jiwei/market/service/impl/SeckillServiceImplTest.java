package com.jiwei.market.service.impl;

import com.jiwei.common.context.BaseContext;
import com.jiwei.common.constant.MessageConstant;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.dto.SeckillBuyDTO;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.market.seckill.mq.SeckillMessage;
import com.jiwei.pojo.entity.SeckillActivity;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SeckillServiceImplTest {

    private final SeckillActivityMapper activityMapper = mock(SeckillActivityMapper.class);
    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
    private final TransactionMQProducer transactionProducer = mock(TransactionMQProducer.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> objValueOps = mock(ValueOperations.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);

    private SeckillServiceImpl newService() {
        SeckillServiceImpl svc = new SeckillServiceImpl();
        ReflectionTestUtils.setField(svc, "seckillActivityMapper", activityMapper);
        ReflectionTestUtils.setField(svc, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(svc, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(svc, "transactionProducer", transactionProducer);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForValue()).thenReturn(objValueOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        return svc;
    }

    private SeckillActivity runningActivity() {
        SeckillActivity act = new SeckillActivity();
        act.setId(5L);
        act.setDishId(5L);
        act.setStartTime(LocalDateTime.now().minusHours(1));
        act.setEndTime(LocalDateTime.now().plusHours(1));
        act.setLimitPerUser(1);
        act.setSeckillPrice(new BigDecimal("9.90"));
        return act;
    }

    private TransactionSendResult rollbackResult() {
        // TransactionSendResult.getLocalTransactionState() 无法被 Mockito stub（嵌套 stubbing 冲突），
        // 该类有公开 setter，直接用真实对象构造。
        TransactionSendResult r = new TransactionSendResult();
        r.setLocalTransactionState(LocalTransactionState.ROLLBACK_MESSAGE);
        return r;
    }

    @AfterEach
    void cleanup() {
        BaseContext.removeCurrentId();
    }

    @Test
    void rollbackWithoutFailReasonShouldThrowStockOut() throws Exception {
        when(activityMapper.selectById(5L)).thenReturn(runningActivity());
        when(transactionProducer.sendMessageInTransaction(any(), any())).thenReturn(rollbackResult());
        when(valueOps.get(any())).thenReturn(null);  // 无失败原因标记 → 售罄
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_STOCK_OUT);
    }

    @Test
    void rollbackWithRepeatReasonShouldThrowRepeat() throws Exception {
        when(activityMapper.selectById(5L)).thenReturn(runningActivity());
        when(transactionProducer.sendMessageInTransaction(any(), any())).thenReturn(rollbackResult());
        when(valueOps.get(any())).thenReturn("repeat");  // 失败原因=重复秒杀
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_REPEAT);
    }

    @Test
    void commitStateShouldReturnOrderNo() throws Exception {
        when(activityMapper.selectById(5L)).thenReturn(runningActivity());
        TransactionSendResult commitResult = new TransactionSendResult();
        commitResult.setLocalTransactionState(LocalTransactionState.COMMIT_MESSAGE);
        when(transactionProducer.sendMessageInTransaction(any(), any())).thenReturn(commitResult);
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        Long orderNo = svc.seckillBuy(dto);
        // 返回值是数字串的 Long（雪花 ID），断言不为空即可
        org.assertj.core.api.Assertions.assertThat(orderNo).isNotNull();
    }

    @Test
    void preDeductShouldReturnFalseAndSwallowErrorWhenMarkWriteFails() {
        // Lua 预扣成功（返回 1），但预扣标记写入 Redis 抛异常：
        // 应捕获异常（不向上抛）、返回 false，使 listener 走 ROLLBACK（消息不投递、不建单）
        when(activityMapper.selectById(5L)).thenReturn(runningActivity());
        when(stringRedisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(), any())).thenReturn(1L);
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        SeckillServiceImpl svc = newService();
        SeckillMessage message = new SeckillMessage("order-pre-1", 404L, 5L, 5L, 1, new BigDecimal("9.90"));

        // 不应向上抛异常（标记写失败被 catch 并回补库存）
        boolean ok = svc.preDeductSeckillStock(message);

        assertThat(ok).isFalse();
        // 确实尝试写标记了（走到写标记分支才抛异常）
        verify(valueOps).set(eq("seckill:prededuct:order-pre-1"), eq("1"), any(Duration.class));
        // 已扣库存被回补（revertRedisStock: stock +1, user 计数 -1）
        verify(objValueOps).increment("seckill:stock:5:5", 1L);
        verify(hashOps).increment("seckill:users:5", "404", -1L);
    }

    @Test
    void preDeductShouldSucceedAndWriteMark() {
        when(activityMapper.selectById(5L)).thenReturn(runningActivity());
        when(stringRedisTemplate.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(), any())).thenReturn(1L);
        SeckillServiceImpl svc = newService();
        SeckillMessage message = new SeckillMessage("order-pre-2", 404L, 5L, 5L, 1, new BigDecimal("9.90"));

        boolean ok = svc.preDeductSeckillStock(message);

        assertThat(ok).isTrue();
        // 预扣标记已写
        verify(valueOps).set(eq("seckill:prededuct:order-pre-2"), eq("1"), any(Duration.class));
    }
}
