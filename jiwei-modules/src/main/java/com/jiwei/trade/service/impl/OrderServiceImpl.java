package com.jiwei.trade.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jiwei.common.context.BaseContext;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.common.result.PageResult;
import com.jiwei.market.seckill.mq.SeckillMessage;
import com.jiwei.market.service.SeckillService;
import com.jiwei.merchant.mapper.DishMapper;
import com.jiwei.merchant.mapper.MerchantInfoMapper;
import com.jiwei.pojo.entity.*;
import com.jiwei.trade.dto.OrderSubmitDTO;
import com.jiwei.trade.mapper.OrderDetailMapper;
import com.jiwei.trade.mapper.OrderMapper;
import com.jiwei.trade.mq.NotifyMessageProducer;
import com.jiwei.trade.mq.OrderMessageProducer;
import com.jiwei.trade.pay.core.model.RefundResult;
import com.jiwei.trade.pay.service.PayOrderService;
import com.jiwei.trade.service.OrderService;
import com.jiwei.user.mapper.AddressBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务实现类
 * 核心流程：下单（Redisson锁防重 + RocketMQ延迟消息） → 支付 → 状态流转
 */
@DS("trade")
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PayOrderService payOrderService;

    @Autowired
    private NotifyMessageProducer notifyMessageProducer;

    @Autowired
    private OrderMessageProducer orderMessageProducer;

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private com.jiwei.market.service.CouponService couponService;

    /**
     * 用户提交订单（核心流程）
     *
     * @param orderSubmitDTO
     * @return
     */
    @Override
    @DSTransactional
    public Orders submit(OrderSubmitDTO orderSubmitDTO) {
        Long userId = BaseContext.getCurrentId();

        //1、使用Redisson分布式锁防止同一用户并发重复提交
        RLock lock = redissonClient.getLock("order:lock:" + userId);
        try {
            if (!lock.tryLock(10, 30, TimeUnit.SECONDS)) {
                throw new OrderBusinessException("系统繁忙，请稍后再试");
            }

            //2、查询收货地址（校验归属当前用户，防止使用他人地址下单泄露隐私）
            AddressBook address = addressBookMapper.selectById(orderSubmitDTO.getAddressBookId());
            if (address == null) {
                throw new OrderBusinessException("收货地址为空");
            }
            if (!address.getUserId().equals(userId)) {
                throw new OrderBusinessException("收货地址不存在");
            }

            //3、生成订单号（雪花算法）
            String orderNumber = IdUtil.getSnowflakeNextIdStr();

            //4、构建订单实体
            Orders order = Orders.builder()
                    .number(orderNumber)
                    .userId(userId)
                    .merchantId(orderSubmitDTO.getMerchantId())
                    .addressBookId(orderSubmitDTO.getAddressBookId())
                    .addressDetail(address.getProvinceName() + address.getCityName()
                            + address.getDistrictName() + address.getDetail())
                    .amount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .deliveryFee(BigDecimal.ZERO)
                    .payAmount(BigDecimal.ZERO)
                    .payMethod(orderSubmitDTO.getPayMethod())
                    .payStatus(Orders.UN_PAID)
                    .status(Orders.PENDING_PAYMENT)
                    .remark(orderSubmitDTO.getRemark())
                    .build();
            orderMapper.insert(order);

            //5、创建订单明细（从Redis购物车读取并计算金额）
            BigDecimal totalAmount = createOrderDetailsAndCalculate(order.getId(), userId);

            //6、更新订单金额
            order.setAmount(totalAmount);
            order.setPayAmount(totalAmount.subtract(order.getDiscountAmount()));
            orderMapper.updateById(order);

            //7、清空购物车
            redisTemplate.delete("cart:" + userId);

            //8、发送RocketMQ延迟消息（15分钟后检查支付状态）
            sendDelayCheckMessage(order.getId());

            log.info("订单提交成功，orderId: {}, orderNumber: {}, amount: {}",
                    order.getId(), orderNumber, totalAmount);
            return order;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OrderBusinessException("系统异常");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 创建订单明细并计算总金额
     */
    private BigDecimal createOrderDetailsAndCalculate(Long orderId, Long userId) {
        BigDecimal total = BigDecimal.ZERO;

        //1、从Redis购物车读取数据
        String cartKey = "cart:" + userId;
        Map<Object, Object> cartEntries = redisTemplate.opsForHash().entries(cartKey);

        if (cartEntries.isEmpty()) {
            log.warn("购物车为空，userId: {}", userId);
            return total;
        }

        //2、遍历购物车条目创建OrderDetail
        for (Map.Entry<Object, Object> entry : cartEntries.entrySet()) {
            String json = (String) entry.getValue();
            com.alibaba.fastjson2.JSONObject item = com.alibaba.fastjson2.JSON.parseObject(json);

            OrderDetail detail = OrderDetail.builder()
                    .orderId(orderId)
                    .dishId(item.getLong("dishId"))
                    .setmealId(item.getLong("setmealId"))
                    .name(item.getString("name"))
                    .image(item.getString("image"))
                    .dishFlavor(item.getString("dishFlavor"))
                    .amount(item.getBigDecimal("amount"))
                    .number(item.getIntValue("number"))
                    .build();
            orderDetailMapper.insert(detail);

            //3、累加金额
            BigDecimal itemTotal = detail.getAmount().multiply(BigDecimal.valueOf(detail.getNumber()));
            total = total.add(itemTotal);
        }

        log.info("订单明细创建完成，orderId: {}, 明细数: {}, 总金额: {}", orderId, cartEntries.size(), total);
        return total;
    }

    /**
     * 发送RocketMQ延迟消息检查支付状态（延迟20分钟，RocketMQ内置档位）
     */
    private void sendDelayCheckMessage(Long orderId) {
        orderMessageProducer.sendOrderDelayCheck(orderId);
    }

    @Override
    // 不用事务：秒杀库存回补需跨 market 库（@DS("market")），类级 @DS("trade") + @Transactional 会把连接绑定 trade 导致切换失效（同 handleTimeoutOrder）
    public void cancel(Long orderId, Long userId) {
        //1、查询并校验：订单归属 + 仅待支付可取消
        Orders order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new OrderBusinessException("订单不存在");
        }
        if (!order.getStatus().equals(Orders.PENDING_PAYMENT)) {
            throw new OrderBusinessException("当前订单状态不允许取消");
        }

        //2、CAS 置取消：仅当仍处于待支付才成功，避免与支付入账并发把已支付订单误取消
        int updated = orderMapper.cancelByUser(orderId, "用户主动取消");
        if (updated == 0) {
            throw new OrderBusinessException("当前订单状态不允许取消");
        }

        //3、秒杀订单回补库存（DB 库存 + Redis 库存 + 用户限购计数）
        if (order.getIsSeckill() != null && order.getIsSeckill() == 1) {
            seckillService.restoreSeckillOnTimeout(order.getSeckillActivityId(), order.getUserId());
        }
        log.info("订单已取消，orderId: {}", orderId);
    }

    @Override
    @Transactional
    public void pay(Long orderId, Long userId, String channelCode) {
        //1、查询并校验订单
        Orders order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new OrderBusinessException("订单不存在");
        }
        if (!order.getStatus().equals(Orders.PENDING_PAYMENT)) {
            throw new OrderBusinessException("订单状态异常");
        }

        //2、委托支付中台：创建支付单 → 渠道下单
        // 余额/mock 渠道同步入账，状态由 OrderPaidConsumer 回写；微信渠道返回支付参数待回调
        com.jiwei.trade.pay.core.model.PayResult payResult =
                payOrderService.createPayOrder(order.getNumber(), channelCode, order.getPayAmount(), userId);
        //3、微信 mock 渠道：发起后自动模拟回调确认，完成开发环境支付闭环（生产 real 模式由真实回调完成）
        if (payResult != null && !payResult.paid()) {
            payOrderService.mockConfirmIfWechat(order.getNumber());
        }
    }

    @Override
    @Transactional
    public void confirm(Long orderId) {
        //1、查询订单
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        //2、校验订单状态（只有待接单状态可以接单）
        if (!order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException("当前订单状态不可接单");
        }

        //3、更新状态
        order.setStatus(Orders.PREPARING);
        orderMapper.updateById(order);
        log.info("商家接单，orderId: {}", orderId);

        //4、定向推送通知用户"商家已接单"
        notifyMessageProducer.sendToUser(order.getUserId(), "接单", "您的订单已被商家接单");
    }

    @Override
    @Transactional
    public void reject(Long orderId, String reason) {
        //1、查询订单
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        //2、校验状态
        if (!order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException("当前订单状态不可拒单");
        }

        //3、更新为已取消
        order.setStatus(Orders.CANCELLED);
        order.setCancelReason(reason != null ? reason : "商家拒单");
        order.setCancelTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("商家拒单，orderId: {}, reason: {}", orderId, reason);

        //4、已支付订单触发退款
        if (order.getPayStatus() != null && order.getPayStatus() == 1) {
            payOrderService.refund(order, reason != null ? reason : "商家拒单退款");
        }
        //5、秒杀订单回补库存
        if (order.getIsSeckill() != null && order.getIsSeckill() == 1) {
            seckillService.restoreSeckillOnTimeout(order.getSeckillActivityId(), order.getUserId());
        }
        //6、释放已使用优惠券
        if (order.getUserCouponId() != null) {
            couponService.release(order.getUserCouponId());
        }
    }

    @Override
    public void prepare(Long orderId) {
        //1、查询订单
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        //2、校验状态（只有备货中可以完成备货）
        if (!order.getStatus().equals(Orders.PREPARING)) {
            throw new OrderBusinessException("当前订单状态不可备货完成");
        }

        //3、更新为待取餐
        order.setStatus(Orders.AWAITING_PICKUP);
        orderMapper.updateById(order);
        log.info("商家备货完成，orderId: {}", orderId);
    }

    @Override
    @Transactional
    public void complete(Long orderId) {
        //1、查询订单
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        //2、校验状态
        if (!order.getStatus().equals(Orders.AWAITING_PICKUP)) {
            throw new OrderBusinessException("当前订单状态不可完成");
        }

        //3、更新为已完成
        order.setStatus(Orders.COMPLETED);
        order.setDeliveryTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("订单已完成，orderId: {}", orderId);
    }

    @Override
    @Transactional
    public void refund(Long orderId) {
        //1、查询订单
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }

        //2、只允许已支付或已完成的订单退款
        if (!order.getPayStatus().equals(Orders.PAID)
                && !order.getStatus().equals(Orders.COMPLETED)) {
            throw new OrderBusinessException("当前订单状态不可退款");
        }

        //3、委托支付中台退款（余额渠道退回账户，微信渠道骨架）
        RefundResult result = payOrderService.refund(order, "用户退款");
        if (!result.success()) {
            throw new OrderBusinessException(result.message());
        }

        //4、更新退款状态
        order.setStatus(Orders.REFUNDED);
        order.setPayStatus(Orders.REFUND);
        orderMapper.updateById(order);
        log.info("退款处理完成，orderId: {}", orderId);
    }

    @Override
    public PageResult pageQuery(Integer page, Integer pageSize, Integer status) {
        //1、构建分页条件（必须按当前登录用户过滤，防止越权查看他人订单）
        Page<Orders> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getUserId, BaseContext.getCurrentId())
               .eq(status != null, Orders::getStatus, status)
               .orderByDesc(Orders::getCreateTime);

        //2、执行分页查询
        Page<Orders> result = orderMapper.selectPage(p, wrapper);
        //3、填充店铺名称 + 订单明细
        fillMerchantNames(result.getRecords());
        fillOrderDetails(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult adminPageQuery(Integer page, Integer pageSize, Long merchantId, Integer status) {
        //管理端/商家端按店铺过滤（merchantId 为空则平台看全部）；不能用登录人 userId 过滤，否则商家查不到单
        Page<Orders> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(merchantId != null, Orders::getMerchantId, merchantId)
               .eq(status != null, Orders::getStatus, status)
               .orderByDesc(Orders::getCreateTime);

        Page<Orders> result = orderMapper.selectPage(p, wrapper);
        fillMerchantNames(result.getRecords());
        fillOrderDetails(result.getRecords());
        return new PageResult(result.getTotal(), result.getRecords());
    }

    @Override
    public Orders getOrderDetail(Long id) {
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new OrderBusinessException("订单不存在");
        }
        //越权校验：只能查看自己的订单
        if (!order.getUserId().equals(BaseContext.getCurrentId())) {
            throw new OrderBusinessException("无权查看该订单");
        }
        //填充明细 + 店铺名
        LambdaQueryWrapper<OrderDetail> detailWrapper = new LambdaQueryWrapper<>();
        detailWrapper.eq(OrderDetail::getOrderId, id);
        order.setOrderDetails(orderDetailMapper.selectList(detailWrapper));
        fillMerchantNames(java.util.Collections.singletonList(order));
        return order;
    }

    /**
     * 批量填充订单店铺名称（跨库查询 merchant，无事务方法内数据源可安全切换）
     */
    private void fillMerchantNames(List<Orders> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> merchantIds = orders.stream()
                .map(Orders::getMerchantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (merchantIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = merchantInfoMapper.selectBatchIds(merchantIds).stream()
                .collect(Collectors.toMap(MerchantInfo::getId, MerchantInfo::getName, (a, b) -> a));
        orders.forEach(o -> o.setMerchantName(nameMap.getOrDefault(o.getMerchantId(), "店铺")));
    }

    /**
     * 批量填充订单明细（列表页展示菜品/件数）
     */
    private void fillOrderDetails(List<Orders> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> orderIds = orders.stream()
                .map(Orders::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (orderIds.isEmpty()) {
            return;
        }
        List<OrderDetail> details = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().in(OrderDetail::getOrderId, orderIds));
        Map<Long, List<OrderDetail>> detailMap = details.stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId));
        orders.forEach(o -> o.setOrderDetails(detailMap.getOrDefault(o.getId(), java.util.Collections.emptyList())));
    }

    /**
     * 再来一单：把原订单明细重新加入购物车（Redis cart）
     */
    @Override
    @Transactional
    public void repetition(Long orderId) {
        Long userId = BaseContext.getCurrentId();
        //1、查询订单并校验归属
        Orders order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new OrderBusinessException("订单不存在");
        }
        //2、查询订单明细
        List<OrderDetail> details = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, orderId));
        if (details.isEmpty()) {
            throw new OrderBusinessException("订单明细为空");
        }
        //3、按购物车结构写入 Redis，便于前端直接加购
        String cartKey = "cart:" + userId;
        for (OrderDetail d : details) {
            String field = d.getDishId() != null
                    ? d.getDishId() + (d.getDishFlavor() == null || d.getDishFlavor().isEmpty() ? "" : "_" + d.getDishFlavor())
                    : "setmeal_" + d.getSetmealId();
            Object existing = redisTemplate.opsForHash().get(cartKey, field);
            com.alibaba.fastjson2.JSONObject item;
            if (existing != null) {
                item = com.alibaba.fastjson2.JSON.parseObject((String) existing);
                item.put("number", item.getIntValue("number") + d.getNumber());
            } else {
                item = new com.alibaba.fastjson2.JSONObject();
                item.put("dishId", d.getDishId());
                item.put("setmealId", d.getSetmealId());
                item.put("merchantId", order.getMerchantId());
                item.put("name", d.getName());
                item.put("image", d.getImage());
                item.put("dishFlavor", d.getDishFlavor());
                item.put("amount", d.getAmount());
                item.put("number", d.getNumber());
            }
            redisTemplate.opsForHash().put(cartKey, field, item.toJSONString());
        }
        redisTemplate.expire(cartKey, 30, TimeUnit.DAYS);
        log.info("再来一单，userId: {}, orderId: {}", userId, orderId);
    }

    /**
     * 处理超时未支付订单（延迟消息消费触发）。
     * 不用事务：秒杀库存回补需跨 market 库（@DS("market")），类级 @DS("trade") + @Transactional 会把连接绑定 trade 导致切换失效。
     * 用 CAS 条件更新：仅当订单仍处于待支付状态才取消，避免与支付入账并发时把已支付订单误取消。
     */
    @Override
    public void handleTimeoutOrder(Long orderId) {
        //1、CAS 取消：仅待支付(1)可置为已取消(6)，返回 0 说明状态已变更（已支付/已取消），直接跳过
        int updated = orderMapper.cancelPendingIfUnpaid(orderId);
        if (updated == 0) {
            log.info("超时订单状态已变更，跳过取消: orderId={}", orderId);
            return;
        }

        //2、回读订单用于回补（CAS 成功后状态才是已取消）
        Orders order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("超时订单不存在，orderId: {}", orderId);
            return;
        }
        log.info("超时订单已自动取消，orderId: {}", orderId);

        //3、秒杀订单回补库存（DB + Redis + 用户限购）
        if (order.getIsSeckill() != null && order.getIsSeckill() == 1) {
            seckillService.restoreSeckillOnTimeout(order.getSeckillActivityId(), order.getUserId());
        }
        //4、释放已使用优惠券
        if (order.getUserCouponId() != null) {
            couponService.release(order.getUserCouponId());
        }
    }

    @Override
    public boolean seckillOrderExists(String orderNo) {
        return orderMapper.selectCount(
                new LambdaQueryWrapper<Orders>().eq(Orders::getNumber, orderNo)) > 0;
    }

    @Override
    // 不用事务：类级 @DS("trade") 会让事务内连接绑定 trade，dishMapper(merchant) 切换失效；单条 insert 无需原子性
    public Long createSeckillOrder(SeckillMessage message) {
        //1、从菜品查 merchantId（秒杀订单必须关联商户）
        Dish dish = dishMapper.selectById(message.dishId());
        if (dish == null) {
            throw new OrderBusinessException("秒杀菜品不存在");
        }

        //2、幂等：同一 orderNo 已建单（MQ 重复消费）直接返回
        Orders existing = orderMapper.selectOne(
                new LambdaQueryWrapper<Orders>().eq(Orders::getNumber, message.orderNo()));
        if (existing != null) {
            return existing.getId();
        }

        //3、查重：该用户对该活动是否已有未取消/未退款的秒杀订单（已取消订单允许重新抢购）
        Long activeCount = orderMapper.selectCount(
                new LambdaQueryWrapper<Orders>()
                        .eq(Orders::getUserId, message.userId())
                        .eq(Orders::getSeckillActivityId, message.activityId())
                        .notIn(Orders::getStatus, Orders.CANCELLED, Orders.REFUNDED));
        if (activeCount != null && activeCount > 0) {
            throw new OrderBusinessException("重复秒杀");
        }

        //4、组装秒杀订单
        Orders order = Orders.builder()
                .number(message.orderNo())
                .userId(message.userId())
                .merchantId(dish.getMerchantId())
                .amount(message.payAmount())
                .discountAmount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .payAmount(message.payAmount())
                .payStatus(Orders.UN_PAID)
                .status(Orders.PENDING_PAYMENT)
                .isSeckill(1)
                .seckillActivityId(message.activityId())
                .build();

        //5、插入订单；number 唯一索引兜底防并发重复建单
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            Orders dup = orderMapper.selectOne(
                    new LambdaQueryWrapper<Orders>().eq(Orders::getNumber, message.orderNo()));
            if (dup != null) {
                return dup.getId();
            }
            throw new OrderBusinessException("重复秒杀");
        }

        //6、补建秒杀订单明细（订单列表/详情页需要展示菜品）
        OrderDetail detail = OrderDetail.builder()
                .orderId(order.getId())
                .dishId(message.dishId())
                .name(dish.getName())
                .image(dish.getImage())
                .dishFlavor("")
                .amount(message.payAmount())
                .number(message.quantity())
                .build();
        orderDetailMapper.insert(detail);

        //7、秒杀订单同样发送延迟消息，超时未支付回补库存
        sendDelayCheckMessage(order.getId());
        return order.getId();
    }
}
