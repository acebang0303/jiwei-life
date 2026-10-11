package com.jiwei.trade.service;

import com.jiwei.market.seckill.mq.SeckillMessage;
import com.jiwei.trade.dto.OrderSubmitDTO;
import com.jiwei.common.result.PageResult;
import com.jiwei.pojo.entity.Orders;

/**
 * 订单服务接口
 */
public interface OrderService {

    /**
     * 用户提交订单
     */
    Orders submit(OrderSubmitDTO orderSubmitDTO);

    /**
     * 用户取消订单
     */
    void cancel(Long orderId, Long userId);

    /**
     * 用户支付订单（渠道支付）
     */
    void pay(Long orderId, Long userId, String channelCode);

    /**
     * 商家接单
     */
    void confirm(Long orderId);

    /**
     * 商家拒单
     */
    void reject(Long orderId, String reason);

    /**
     * 商家备货完成（备货中 → 待取餐）
     */
    void prepare(Long orderId);

    /**
     * 完成订单
     */
    void complete(Long orderId);

    /**
     * 退款处理
     */
    void refund(Long orderId);

    /**
     * 再来一单：原订单明细重新加入购物车
     */
    void repetition(Long orderId);

    /**
     * 分页查询订单（C端，按当前登录用户过滤）
     */
    PageResult pageQuery(Integer page, Integer pageSize, Integer status);

    /**
     * 分页查询订单（管理端/商家端，按 merchantId 过滤；merchantId 为空查全部）
     */
    PageResult adminPageQuery(Integer page, Integer pageSize, Long merchantId, Integer status);

    /**
     * 订单详情（含明细、店铺名），校验归属当前用户
     */
    Orders getOrderDetail(Long id);

    /**
     * 创建秒杀订单（trade 库建单，uk_user_activity 防重）
     */
    Long createSeckillOrder(SeckillMessage message);

    /**
     * 秒杀订单是否已存在（按 orderNo 幂等判断，供消费者避免重复扣库存）
     */
    boolean seckillOrderExists(String orderNo);

    /**
     * 处理超时未支付订单（延迟消息消费触发）：取消订单 + 秒杀库存回补
     */
    void handleTimeoutOrder(Long orderId);
}
