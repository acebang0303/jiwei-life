package com.jiwei.market.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.constant.MessageConstant;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.common.result.PageResult;
import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.jiwei.market.dto.SeckillBuyDTO;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.market.seckill.mq.SeckillMessage;
import com.jiwei.market.service.SeckillService;
import com.jiwei.merchant.mapper.DishMapper;
import com.jiwei.merchant.mapper.MerchantInfoMapper;
import com.jiwei.pojo.entity.Dish;
import com.jiwei.pojo.entity.SeckillActivity;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 秒杀服务实现类
 *
 * 完整秒杀链路:
 * 1. 活动创建 → 库存预热到 Redis
 * 2. 用户秒杀: Redis Lua 脚本原子执行 (校验时间 + 查重 + 扣库存)
 * 3. 异步落库: RocketMQ 消息 → 消费者创建订单
 * 4. 超时回补: RocketMQ延迟消息 → 回补库存 + 释放优惠券
 */
@DS("market")
@Service
@Slf4j
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TransactionMQProducer transactionProducer;

    private static final String PRE_DEDUCT_KEY = "seckill:prededuct:";
    private static final String FAIL_REASON_KEY = "seckill:failreason:";
    private static final String SECKILL_ORDER_TOPIC = "seckill-order-topic";

    //Redis Lua脚本: 原子执行秒杀逻辑（限购用 HINCRBY 计数，支持按用户回滚）
    private static final String SECKILL_LUA_SCRIPT =
            "-- 秒杀Lua脚本: 校验库存 + 限购计数 + 扣库存\n" +
            "local stockKey = KEYS[1]       -- seckill:stock:{activityId}:{dishId}\n" +
            "local userKey = KEYS[2]        -- seckill:users:{activityId} (HASH: field=userId, value=已购数量)\n" +
            "local userId = ARGV[1]\n" +
            "local limitPerUser = tonumber(ARGV[2])\n" +
            "\n" +
            "-- 1. 检查库存\n" +
            "local stock = tonumber(redis.call('GET', stockKey) or '0')\n" +
            "if stock <= 0 then\n" +
            "    return -1  -- 库存不足\n" +
            "end\n" +
            "\n" +
            "-- 2. 检查限购\n" +
            "local userCount = tonumber(redis.call('HGET', userKey, userId) or '0')\n" +
            "if userCount >= limitPerUser then\n" +
            "    return -2  -- 超过限购\n" +
            "end\n" +
            "\n" +
            "-- 3. 扣减库存 + 限购计数\n" +
            "redis.call('DECR', stockKey)\n" +
            "redis.call('HINCRBY', userKey, userId, 1)\n" +
            "\n" +
            "return 1  -- 秒杀成功";

    @Override
    @Transactional
    public void createActivity(SeckillActivity activity) {
        //1、保存秒杀活动
        activity.setStatus(0); //未开始
        seckillActivityMapper.insert(activity);
        log.info("创建秒杀活动成功，activityId: {}", activity.getId());

        //2、活动库存预热到Redis（活动开始前5分钟由定时任务执行）
        //TODO: 预热逻辑由 @Scheduled 定时任务处理
    }

    @Override
    public PageResult pageActivity(int page, int pageSize) {
        //1、构建分页条件
        Page<SeckillActivity> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(SeckillActivity::getCreateTime);

        //2、执行分页查询
        Page<SeckillActivity> result = seckillActivityMapper.selectPage(p, wrapper);

        //3、按当前时间动态计算状态（未开始/进行中/已结束），随时间推移自动变化
        LocalDateTime now = LocalDateTime.now();
        result.getRecords().forEach(a -> a.setStatus(calcStatus(a, now)));
        fillDishInfo(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public List<SeckillActivity> listRunning() {
        //1、查询进行中的秒杀活动（按时间窗口过滤，不依赖静态 status）
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.le(SeckillActivity::getStartTime, now)
               .ge(SeckillActivity::getEndTime, now);
        List<SeckillActivity> list = seckillActivityMapper.selectList(wrapper);
        //2、实时库存：优先取 Redis（与购买用同一数据源），避免"列表显示 20 实际已抢光"
        list.forEach(a -> {
            a.setStatus(1);
            String stockKey = "seckill:stock:" + a.getId() + ":" + a.getDishId();
            String redisStock = stringRedisTemplate.opsForValue().get(stockKey);
            if (redisStock != null) {
                try {
                    a.setStock(Integer.valueOf(redisStock));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        fillDishInfo(list);
        return list;
    }

    /**
     * 批量填充秒杀菜品的菜品名/店铺名（跨库查 dish / merchant_info）
     */
    private void fillDishInfo(List<SeckillActivity> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        List<Long> dishIds = activities.stream()
                .map(SeckillActivity::getDishId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (dishIds.isEmpty()) {
            return;
        }
        List<Dish> dishes = dishMapper.selectBatchIds(dishIds);
        Map<Long, Dish> dishMap = dishes.stream()
                .collect(Collectors.toMap(Dish::getId, d -> d));
        List<Long> merchantIds = dishes.stream()
                .map(Dish::getMerchantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> merchantMap = merchantIds.isEmpty() ? java.util.Collections.emptyMap()
                : merchantInfoMapper.selectBatchIds(merchantIds).stream()
                        .collect(Collectors.toMap(
                                com.jiwei.pojo.entity.MerchantInfo::getId,
                                com.jiwei.pojo.entity.MerchantInfo::getName,
                                (a, b) -> a));
        activities.forEach(a -> {
            Dish dish = dishMap.get(a.getDishId());
            if (dish != null) {
                a.setDishName(dish.getName());
                a.setMerchantName(merchantMap.getOrDefault(dish.getMerchantId(), ""));
            }
        });
    }

    /**
     * 动态计算活动状态：0未开始 1进行中 2已结束
     */
    private int calcStatus(SeckillActivity activity, LocalDateTime now) {
        if (activity.getStartTime() == null || activity.getEndTime() == null) {
            return 1;
        }
        if (now.isBefore(activity.getStartTime())) {
            return 0;
        }
        if (now.isAfter(activity.getEndTime())) {
            return 2;
        }
        return 1;
    }

    @Override
    public SeckillActivity getActivityById(Long id) {
        //1、查询秒杀活动详情
        return seckillActivityMapper.selectById(id);
    }

    @Override
    public Long seckillBuy(SeckillBuyDTO dto) {
        Long userId = BaseContext.getCurrentId();
        Long activityId = dto.getActivityId();

        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new OrderBusinessException("秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new OrderBusinessException(MessageConstant.SECKILL_NOT_STARTED);
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new OrderBusinessException(MessageConstant.SECKILL_ENDED);
        }

        String orderNo = IdUtil.getSnowflakeNextIdStr();
        SeckillMessage message = new SeckillMessage(
                orderNo, userId, activityId, dto.getDishId(), 1, activity.getSeckillPrice());

        // 事务消息：本地事务(preDeduct)由 listener 执行，与消息投递原子化
        TransactionSendResult sendResult;
        try {
            sendResult = transactionProducer.sendMessageInTransaction(
                    new Message(SECKILL_ORDER_TOPIC,
                            JSON.toJSONString(message).getBytes(StandardCharsets.UTF_8)),
                    message);
        } catch (Exception e) {
            // 半消息发送失败：本地事务未执行，库存未扣，直接报错即可
            log.error("秒杀事务消息发送失败: userId={}, activityId={}, orderNo={}", userId, activityId, orderNo, e);
            throw new OrderBusinessException("秒杀请求繁忙，请稍后重试");
        }

        if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
            throw buildPreDeductFailException(orderNo);
        }
        log.info("秒杀事务消息已发送，userId: {}, activityId: {}, orderNo: {}", userId, activityId, orderNo);
        return Long.valueOf(orderNo);
    }

    /**
     * 依据预扣失败原因构造秒杀异常：存在 "repeat" 标记 → 重复秒杀，否则默认售罄。
     */
    private OrderBusinessException buildPreDeductFailException(String orderNo) {
        String reasonKey = FAIL_REASON_KEY + orderNo;
        String reason = stringRedisTemplate.opsForValue().get(reasonKey);
        if (reason != null) {
            stringRedisTemplate.delete(reasonKey);
        }
        if ("repeat".equals(reason)) {
            return new OrderBusinessException(MessageConstant.SECKILL_REPEAT);
        }
        return new OrderBusinessException(MessageConstant.SECKILL_STOCK_OUT);
    }

    /**
     * 抽取私有 Lua 执行方法：校验库存 + 限购 + 扣减，返回原始返回码。
     * 返回码：1=预扣成功，-1=库存不足，-2=超过限购；null（Redis 异常）归一化为 0，按售罄处理。
     */
    private long executeSeckillLua(Long activityId, Long dishId, Long userId, int limitPerUser) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(SECKILL_LUA_SCRIPT);
        redisScript.setResultType(Long.class);
        String stockKey = "seckill:stock:" + activityId + ":" + dishId;
        String userKey = "seckill:users:" + activityId;
        Long result = stringRedisTemplate.execute(redisScript,
                Arrays.asList(stockKey, userKey),
                userId.toString(), String.valueOf(limitPerUser));
        return result == null ? 0L : result;
    }

    @Override
    public boolean preDeductSeckillStock(SeckillMessage message) {
        SeckillActivity activity = seckillActivityMapper.selectById(message.activityId());
        if (activity == null) {
            return false;
        }
        long result = executeSeckillLua(message.activityId(), message.dishId(),
                message.userId(), activity.getLimitPerUser());
        if (result == 1) {
            String key = PRE_DEDUCT_KEY + message.orderNo();
            long ttlSeconds = Math.max(30 * 60L,
                    java.time.Duration.between(java.time.LocalDateTime.now(), activity.getEndTime()).getSeconds());
            try {
                stringRedisTemplate.opsForValue().set(key, "1", java.time.Duration.ofSeconds(ttlSeconds));
            } catch (Exception e) {
                // 标记写失败：回补已扣库存，返回 false 触发消息回滚
                revertRedisStock(message.activityId(), message.dishId(),
                        message.userId(), message.quantity());
                log.error("秒杀预扣标记写失败，已回滚库存: orderNo={}", message.orderNo(), e);
                return false;
            }
            return true;
        }
        if (result == -2) {
            // 超限购：写短 TTL 失败原因标记，供 seckillBuy 区分「重复秒杀」与「售罄」
            try {
                stringRedisTemplate.opsForValue().set(FAIL_REASON_KEY + message.orderNo(),
                        "repeat", java.time.Duration.ofSeconds(10));
            } catch (Exception e) {
                log.warn("秒杀限购失败原因标记写失败，将按售罄提示: orderNo={}", message.orderNo(), e);
            }
        }
        // -1 售罄（或 0 异常）：不写标记，默认售罄
        return false;
    }

    @Override
    public boolean isPreDeducted(String orderNo) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(PRE_DEDUCT_KEY + orderNo));
    }

    @Override
    public boolean deductStock(Long activityId, int quantity) {
        return seckillActivityMapper.deductStock(activityId, quantity) > 0;
    }

    @Override
    public void restoreStock(Long activityId, int quantity) {
        seckillActivityMapper.restoreStock(activityId, quantity);
    }

    @Override
    public void revertRedisStock(Long activityId, Long dishId, Long userId, int quantity) {
        String stockKey = "seckill:stock:" + activityId + ":" + dishId;
        String userKey = "seckill:users:" + activityId;
        redisTemplate.opsForValue().increment(stockKey, quantity);
        redisTemplate.opsForHash().increment(userKey, String.valueOf(userId), -quantity);
    }

    @Override
    public void restoreSeckillOnTimeout(Long activityId, Long userId) {
        //1、查活动拿 dishId（订单表只存 activityId，dishId 经活动反查）
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            log.warn("秒杀活动不存在，跳过库存回补: activityId={}", activityId);
            return;
        }

        //2、回补 DB 库存（秒杀下单时已 deductStock）
        seckillActivityMapper.restoreStock(activityId, 1);

        //3、回补 Redis 库存 + 用户限购计数
        revertRedisStock(activityId, activity.getDishId(), userId, 1);
        log.info("秒杀订单超时回补完成: activityId={}, dishId={}, userId={}", activityId, activity.getDishId(), userId);
    }
}
