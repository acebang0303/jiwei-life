package com.jiwei.market.service;

import com.jiwei.common.result.PageResult;
import com.jiwei.market.dto.SeckillBuyDTO;
import com.jiwei.market.seckill.mq.SeckillMessage;
import com.jiwei.pojo.entity.SeckillActivity;

import java.util.List;

/**
 * 秒杀服务接口
 * 核心链路：Redis Lua原子操作 → RocketMQ异步落库
 */
public interface SeckillService {

    /**
     * 创建秒杀活动
     */
    void createActivity(SeckillActivity activity);

    /**
     * 分页查询秒杀活动
     */
    PageResult pageActivity(int page, int pageSize);

    /**
     * 查询进行中的秒杀活动
     */
    List<SeckillActivity> listRunning();

    /**
     * 查询活动详情
     */
    SeckillActivity getActivityById(Long id);

    /**
     * 秒杀抢购
     * @return 订单ID
     */
    Long seckillBuy(SeckillBuyDTO dto);

    /**
     * DB 兜底扣库存（market 库，CAS 防超卖）
     */
    boolean deductStock(Long activityId, int quantity);

    /**
     * 回补 DB 库存（建单失败补偿）
     */
    void restoreStock(Long activityId, int quantity);

    /**
     * 回滚 Redis 预扣库存与用户限购计数
     */
    void revertRedisStock(Long activityId, Long dishId, Long userId, int quantity);

    /**
     * 秒杀订单超时未支付：回补 DB 库存 + Redis 库存 + 用户限购计数
     */
    void restoreSeckillOnTimeout(Long activityId, Long userId);

    /**
     * 事务消息本地事务执行体：Redis Lua 预扣库存 + 限购 + 写预扣标记。返回 true=预扣成功（消息应提交）
     */
    boolean preDeductSeckillStock(SeckillMessage message);

    /**
     * broker 回查：orderNo 对应的预扣标记是否仍在 Redis（在=已扣，提交；不在=回滚）
     */
    boolean isPreDeducted(String orderNo);
}
