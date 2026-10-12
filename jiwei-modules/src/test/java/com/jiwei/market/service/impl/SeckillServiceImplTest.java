package com.jiwei.market.service.impl;

import com.jiwei.common.context.BaseContext;
import com.jiwei.common.constant.MessageConstant;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.dto.SeckillBuyDTO;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.market.seckill.cache.RedisExecutor;
import com.jiwei.market.seckill.cache.SeckillActivityCache;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    private final RedisExecutor redisExecutor = mock(RedisExecutor.class);
    private final SeckillActivityCache activityCache = mock(SeckillActivityCache.class);
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
        ReflectionTestUtils.setField(svc, "redisExecutor", redisExecutor);
        ReflectionTestUtils.setField(svc, "activityCache", activityCache);
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
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rollbackWithoutFailReasonShouldThrowStockOut() throws Exception {
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
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
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
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
    void rollbackWithBusyReasonShouldThrowBusy() throws Exception {
        // Redis 故障（熔断降级）时预扣失败应提示「系统繁忙」而非「售罄」
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
        when(transactionProducer.sendMessageInTransaction(any(), any())).thenReturn(rollbackResult());
        when(valueOps.get(any())).thenReturn("busy");
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_BUSY);
    }

    @Test
    void seckillBuyShouldFailFastWhenRedisDegraded() throws Exception {
        // 熔断器 OPEN 时应快速失败，不再发送半消息
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
        when(redisExecutor.isDegraded()).thenReturn(true);
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_BUSY);
        verify(transactionProducer, never()).sendMessageInTransaction(any(), any());
    }

    @Test
    void commitStateShouldReturnOrderNo() throws Exception {
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
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
    void createActivityShouldRejectLimitPerUserGreaterThanOne() {
        // uk_user_activity 唯一索引是一人一单硬约束，limitPerUser>1 语义冲突，创建期应拒绝
        SeckillActivity act = runningActivity();
        act.setId(null);
        act.setLimitPerUser(3);
        SeckillServiceImpl svc = newService();

        assertThatThrownBy(() -> svc.createActivity(act))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_LIMIT_UNSUPPORTED);
        verify(activityMapper, never()).insert(any(SeckillActivity.class));
        verify(redisExecutor, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void createActivityShouldWarmUpStockOnlyAfterCommit() {
        // 事务提交前不预热（回滚则不预热）；提交后以 DB 值覆盖写 Redis
        SeckillActivity act = runningActivity();
        act.setId(7L);
        act.setStock(50);
        SeckillServiceImpl svc = newService();
        TransactionSynchronizationManager.initSynchronization();

        svc.createActivity(act);

        // 提交前：未预热
        verify(redisExecutor, never()).set(any(), any(), any(Duration.class));
        // 模拟事务提交
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);

        verify(redisExecutor).set(eq("seckill:stock:7:5"), eq("50"), any(Duration.class));
    }

    @Test
    void createActivityWithoutTransactionShouldWarmUpImmediately() {
        // 无事务上下文（如测试/工具调用）时直接预热
        SeckillActivity act = runningActivity();
        act.setId(8L);
        act.setStock(20);
        SeckillServiceImpl svc = newService();

        svc.createActivity(act);

        verify(redisExecutor).set(eq("seckill:stock:8:5"), eq("20"), any(Duration.class));
    }

    @Test
    void preDeductShouldReturnFalseAndSwallowErrorWhenMarkWriteFails() {
        // Lua 预扣成功（返回 1），但预扣标记写入 Redis 抛异常：
        // 应捕获异常（不向上抛）、返回 false，使 listener 走 ROLLBACK（消息不投递、不建单）
        when(redisExecutor.eval(any(DefaultRedisScript.class), anyList(), any(), any(Object[].class)))
                .thenReturn(1L);
        doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        SeckillServiceImpl svc = newService();
        SeckillMessage message = new SeckillMessage("order-pre-1", 404L, 5L, 5L, 1,
                new BigDecimal("9.90"), 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

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
        when(redisExecutor.eval(any(DefaultRedisScript.class), anyList(), any(), any(Object[].class)))
                .thenReturn(1L);
        SeckillServiceImpl svc = newService();
        SeckillMessage message = new SeckillMessage("order-pre-2", 404L, 5L, 5L, 1,
                new BigDecimal("9.90"), 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        boolean ok = svc.preDeductSeckillStock(message);

        assertThat(ok).isTrue();
        // 预扣标记已写
        verify(valueOps).set(eq("seckill:prededuct:order-pre-2"), eq("1"), any(Duration.class));
    }

    @Test
    void preDeductShouldNotQueryDbForLimitPerUser() {
        // 限购配置随消息携带：本地事务不再回查活动表（热路径去 DB）
        when(redisExecutor.eval(any(DefaultRedisScript.class), anyList(), any(), any(Object[].class)))
                .thenReturn(1L);
        SeckillServiceImpl svc = newService();
        SeckillMessage message = new SeckillMessage("order-pre-3", 404L, 5L, 5L, 1,
                new BigDecimal("9.90"), 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        svc.preDeductSeckillStock(message);

        verify(activityMapper, never()).selectById(any());
    }

    @Test
    void preDeductShouldWriteBusyMarkerWhenRedisEvalDegrades() {
        // 熔断降级 eval 返回 null → 归一化 0：返回 false 并尽力写 busy 标记（区分系统繁忙与售罄）
        when(redisExecutor.eval(any(DefaultRedisScript.class), anyList(), any(), any(Object[].class)))
                .thenReturn(null);
        SeckillServiceImpl svc = newService();
        SeckillMessage message = new SeckillMessage("order-pre-4", 404L, 5L, 5L, 1,
                new BigDecimal("9.90"), 1, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        boolean ok = svc.preDeductSeckillStock(message);

        assertThat(ok).isFalse();
        verify(valueOps).set(eq("seckill:failreason:order-pre-4"), eq("busy"), any(Duration.class));
        // 未扣库存，无需回补
        verify(objValueOps, never()).increment(anyString(), anyLong());
    }

    @Test
    void seckillBuyShouldThrowWhenActivityMissingFromCache() throws Exception {
        // 活动不存在（缓存 NULL 标记）→ 明确报错，不触达 DB
        when(activityCache.get(5L)).thenReturn(Optional.empty());
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining("秒杀活动不存在");
        verify(transactionProducer, never()).sendMessageInTransaction(any(), any());
    }

    @Test
    void rollbackWithNotStartReasonShouldThrowNotStarted() throws Exception {
        // Lua 时间窗口校验（-3）写入的 notstart 标记 → 精确提示「未开始」
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
        when(transactionProducer.sendMessageInTransaction(any(), any())).thenReturn(rollbackResult());
        when(valueOps.get(any())).thenReturn("notstart");
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_NOT_STARTED);
    }

    @Test
    void rollbackWithEndedReasonShouldThrowEnded() throws Exception {
        // Lua 时间窗口校验（-4）写入的 ended 标记 → 精确提示「已结束」
        when(activityCache.get(5L)).thenReturn(Optional.of(runningActivity()));
        when(transactionProducer.sendMessageInTransaction(any(), any())).thenReturn(rollbackResult());
        when(valueOps.get(any())).thenReturn("ended");
        SeckillServiceImpl svc = newService();
        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        assertThatThrownBy(() -> svc.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class)
                .hasMessageContaining(MessageConstant.SECKILL_ENDED);
    }
}
