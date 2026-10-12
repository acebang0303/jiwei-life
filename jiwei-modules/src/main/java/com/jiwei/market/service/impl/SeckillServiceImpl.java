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
import com.jiwei.market.seckill.cache.RedisExecutor;
import com.jiwei.market.seckill.cache.SeckillActivityCache;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Autowired
    private RedisExecutor redisExecutor;

    @Autowired
    private SeckillActivityCache activityCache;

    private static final String PRE_DEDUCT_KEY = "seckill:prededuct:";
    private static final String FAIL_REASON_KEY = "seckill:failreason:";
    private static final String SECKILL_ORDER_TOPIC = "seckill-order-topic";

    // Redis Lua脚本: 原子执行秒杀逻辑（时间窗口 + 限购计数 + 库存校验/扣减）
    private static final String SECKILL_LUA_SCRIPT =
            "-- 秒杀Lua脚本: 校验时间窗口 + 限购计数 + 库存\n" +
            "local stockKey = KEYS[1]       -- seckill:stock:{activityId}:{dishId}\n" +
            "local userKey = KEYS[2]        -- seckill:users:{activityId} (HASH: field=userId, value=已购数量)\n" +
            "local userId = ARGV[1]\n" +
            "local limitPerUser = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3]) or 0     -- 当前 epoch 秒\n" +
            "local startTime = tonumber(ARGV[4]) or 0\n" +
            "local endTime = tonumber(ARGV[5]) or 0\n" +
            "\n" +
            "-- 0. 时间窗口原子校验（startTime/endTime 为 0 表示旧消息，跳过）\n" +
            "if startTime > 0 and now < startTime then\n" +
            "    return -3  -- 未开始\n" +
            "end\n" +
            "if endTime > 0 and now > endTime then\n" +
            "    return -4  -- 已结束\n" +
            "end\n" +
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

    /** 预定义脚本对象：避免每次请求重复解析，且统一经 RedisExecutor 熔断器执行 */
    private static final DefaultRedisScript<Long> SECKILL_LUA =
            new DefaultRedisScript<>(SECKILL_LUA_SCRIPT, Long.class);

    @Override
    @Transactional
    public void createActivity(SeckillActivity activity) {
        //1、限购语义校验：orders 表 uk_user_activity(user_id, seckill_activity_id) 唯一索引
        //   是「一人一单」硬约束，limitPerUser > 1 时用户第二次建单必撞索引导致回补库存，
        //   故创建期即拒绝，保证 Lua 限购计数与 DB 约束语义一致
        if (activity.getLimitPerUser() == null || activity.getLimitPerUser() != 1) {
            throw new OrderBusinessException(MessageConstant.SECKILL_LIMIT_UNSUPPORTED);
        }
        //2、保存秒杀活动
        activity.setStatus(0); //未开始
        seckillActivityMapper.insert(activity);
        log.info("创建秒杀活动成功，activityId: {}", activity.getId());

        //3、库存预热 + 元数据缓存：事务提交后执行（insert 回滚则不写）。
        //   不预热会出现 stock key 缺失 → Lua GET or '0' → 新建活动后约 60s 内全部误报售罄，
        //   只能等对账任务 setIfAbsent 兜底
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    warmUpStock(activity);
                    activityCache.put(activity);
                }
            });
        } else {
            warmUpStock(activity);
            activityCache.put(activity);
        }
    }

    /**
     * 库存预热到 Redis（DB 为准，SET 覆盖），TTL 到活动结束。
     * 与 StockWarmUpRunner 使用同一覆盖策略，避免上次残留 0 库存导致"DB 有货、Redis 抢光"。
     */
    private void warmUpStock(SeckillActivity activity) {
        if (activity.getEndTime() == null || activity.getStock() == null) {
            log.warn("活动缺少 stock/endTime，跳过预热: activityId={}", activity.getId());
            return;
        }
        java.time.Duration ttl = java.time.Duration.between(LocalDateTime.now(), activity.getEndTime());
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        String stockKey = "seckill:stock:" + activity.getId() + ":" + activity.getDishId();
        redisExecutor.set(stockKey, String.valueOf(activity.getStock()), ttl);
        log.info("活动创建预热库存: activityId={}, stock={}", activity.getId(), activity.getStock());
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

        // 活动元数据走二级缓存（Caffeine→Redis→DB），热路径 0 次 DB 查询；
        // 不存在的活动已被 NULL 标记缓存，恶意 id 不打到 DB
        SeckillActivity activity = activityCache.get(activityId).orElse(null);
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
        // Redis 熔断期快速失败：预扣 Lua 必然降级，直接提示系统繁忙，避免无效半消息与误导性「售罄」
        if (redisExecutor.isDegraded()) {
            throw new OrderBusinessException(MessageConstant.SECKILL_BUSY);
        }

        String orderNo = IdUtil.getSnowflakeNextIdStr();
        SeckillMessage message = new SeckillMessage(
                orderNo, userId, activityId, dto.getDishId(), 1, activity.getSeckillPrice(),
                activity.getLimitPerUser(), activity.getStartTime(), activity.getEndTime());

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
     * 依据预扣失败原因构造秒杀异常：
     * "busy" → 系统繁忙，"repeat" → 重复秒杀，"notstart"/"ended" → 时间窗口，否则默认售罄。
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
        if ("busy".equals(reason)) {
            return new OrderBusinessException(MessageConstant.SECKILL_BUSY);
        }
        if ("notstart".equals(reason)) {
            return new OrderBusinessException(MessageConstant.SECKILL_NOT_STARTED);
        }
        if ("ended".equals(reason)) {
            return new OrderBusinessException(MessageConstant.SECKILL_ENDED);
        }
        return new OrderBusinessException(MessageConstant.SECKILL_STOCK_OUT);
    }

    private static long epochSeconds(LocalDateTime time) {
        return time == null ? 0L : time.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * 抽取私有 Lua 执行方法：时间窗口 + 限购 + 库存校验/扣减，返回原始返回码。
     * 经 RedisExecutor 熔断器执行：基础设施故障（连接失败/超时/熔断 OPEN）时 fallback 返回 null → 归一化为 0。
     * 返回码：1=预扣成功，-1=库存不足，-2=超过限购，-3=未开始，-4=已结束，0=Redis 故障（区别于售罄）。
     */
    private long executeSeckillLua(SeckillMessage message, int limitPerUser) {
        String stockKey = "seckill:stock:" + message.activityId() + ":" + message.dishId();
        String userKey = "seckill:users:" + message.activityId();
        Long result = redisExecutor.eval(SECKILL_LUA,
                Arrays.asList(stockKey, userKey),
                t -> null,   // 基础设施故障：熔断器已计数，此处降级返回 null
                message.userId().toString(), String.valueOf(limitPerUser),
                String.valueOf(epochSeconds(LocalDateTime.now())),
                String.valueOf(epochSeconds(message.startTime())),
                String.valueOf(epochSeconds(message.endTime())));
        return result == null ? 0L : result;
    }

    /** 短 TTL 失败原因标记（10s），供 seckillBuy 区分具体失败原因 */
    private void writeFailReason(String orderNo, String reason) {
        try {
            stringRedisTemplate.opsForValue().set(FAIL_REASON_KEY + orderNo,
                    reason, java.time.Duration.ofSeconds(10));
        } catch (Exception e) {
            log.warn("秒杀失败原因标记写失败，将按售罄提示: orderNo={}, reason={}", orderNo, reason, e);
        }
    }

    @Override
    public boolean preDeductSeckillStock(SeckillMessage message) {
        // 限购配置随消息携带（seckillBuy 时已查活动），本地事务不再回查 DB；
        // 兼容旧消息（无该字段）缺省按 1
        Integer limitFromMessage = message.limitPerUser();
        int limitPerUser = (limitFromMessage == null || limitFromMessage < 1) ? 1 : limitFromMessage;
        long result = executeSeckillLua(message, limitPerUser);
        if (result == 1) {
            String key = PRE_DEDUCT_KEY + message.orderNo();
            // TTL：活动结束时间与 30min 取大；旧消息无 endTime 时固定 30min
            long ttlSeconds = 30 * 60L;
            if (message.endTime() != null) {
                ttlSeconds = Math.max(ttlSeconds,
                        java.time.Duration.between(LocalDateTime.now(), message.endTime()).getSeconds());
            }
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
        switch ((int) result) {
            case -2:
                // 超限购 → 重复秒杀
                writeFailReason(message.orderNo(), "repeat");
                break;
            case -3:
                // 未开始（Lua 原子校验，防绕过）
                writeFailReason(message.orderNo(), "notstart");
                break;
            case -4:
                // 已结束
                writeFailReason(message.orderNo(), "ended");
                break;
            case 0:
                // Redis 故障（熔断降级）：尽力写 busy 标记区分「系统繁忙」与「售罄」；
                // 若标记写也失败（Redis 完全不可用），退化为默认售罄提示，由熔断恢复后自然纠正
                writeFailReason(message.orderNo(), "busy");
                break;
            default:
                // -1 售罄：不写标记，默认售罄
        }
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
