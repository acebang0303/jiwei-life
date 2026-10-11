package com.jiwei.trade.pay.service.impl;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.pojo.entity.PayChannel;
import com.jiwei.pojo.entity.PayNotifyLog;
import com.jiwei.pojo.entity.PayOrder;
import com.jiwei.trade.pay.core.IPayChannelHandler;
import com.jiwei.trade.pay.core.PayChannelFactory;
import com.jiwei.trade.pay.core.model.PayNotifyResult;
import com.jiwei.trade.pay.core.model.PayResult;
import com.jiwei.trade.pay.core.model.RefundResult;
import com.jiwei.trade.pay.mapper.PayChannelMapper;
import com.jiwei.trade.pay.mapper.PayNotifyLogMapper;
import com.jiwei.trade.pay.mapper.PayOrderMapper;
import com.jiwei.trade.pay.service.PayOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 支付编排服务：验签 + 金额校验 + 幂等入账 + finally 留痕 + 统一下单。
 */
@DS("trade")
@Service
@Slf4j
public class PayOrderServiceImpl implements PayOrderService {

    private static final String ORDER_PAID_TOPIC = "order-paid-topic";

    private final PayOrderMapper payOrderMapper;
    private final PayNotifyLogMapper payNotifyLogMapper;
    private final PayChannelMapper payChannelMapper;
    private final PayChannelFactory payChannelFactory;
    private final DefaultMQProducer rocketMQProducer;

    public PayOrderServiceImpl(PayOrderMapper payOrderMapper,
                               PayNotifyLogMapper payNotifyLogMapper,
                               PayChannelMapper payChannelMapper,
                               PayChannelFactory payChannelFactory,
                               DefaultMQProducer rocketMQProducer) {
        this.payOrderMapper = payOrderMapper;
        this.payNotifyLogMapper = payNotifyLogMapper;
        this.payChannelMapper = payChannelMapper;
        this.payChannelFactory = payChannelFactory;
        this.rocketMQProducer = rocketMQProducer;
    }

    @Override
    @Transactional
    public String handleNotify(String channelCode, Map<String, String> params) {
        IPayChannelHandler handler = payChannelFactory.getHandler(channelCode);
        PayNotifyLog notifyLog = new PayNotifyLog();
        notifyLog.setChannelCode(channelCode);
        notifyLog.setContent(JSON.toJSONString(params));
        try {
            PayChannel channel = payChannelMapper.selectOne(
                    new LambdaQueryWrapper<PayChannel>().eq(PayChannel::getChannelCode, channelCode));
            if (channel == null) {
                notifyLog.setVerifyStatus(2);
                notifyLog.setProcessStatus(2);
                notifyLog.setProcessMsg("渠道配置不存在");
                return handler.notifyFailBody();
            }

            PayNotifyResult result = handler.parseNotify(params, channel);
            notifyLog.setOrderNo(result.orderNo());
            payOrderMapper.increaseNotifyCount(result.orderNo());

            if (!result.verifySuccess()) {
                notifyLog.setVerifyStatus(2);
                notifyLog.setProcessStatus(2);
                notifyLog.setProcessMsg(result.failMsg());
                return handler.notifyFailBody();
            }
            notifyLog.setVerifyStatus(1);

            if (!result.tradeSuccess()) {
                notifyLog.setProcessStatus(1);
                notifyLog.setProcessMsg("交易未成功，忽略入账");
                return handler.notifySuccessBody();
            }

            PayOrder order = payOrderMapper.selectOne(
                    new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, result.orderNo()));
            if (order == null) {
                notifyLog.setProcessStatus(2);
                notifyLog.setProcessMsg("订单不存在");
                return handler.notifyFailBody();
            }

            // 金额校验（防篡改）
            if (result.payAmount() != null
                    && order.getTotalAmount().compareTo(result.payAmount()) != 0) {
                notifyLog.setProcessStatus(2);
                notifyLog.setProcessMsg("金额不一致，已拒绝入账");
                return handler.notifyFailBody();
            }

            int updated = markOrderPaid(order, result.tradeNo(), null);
            notifyLog.setProcessStatus(1);
            notifyLog.setProcessMsg(updated > 0 ? "入账成功" : "订单已是支付成功状态（重复通知，幂等返回）");
            return handler.notifySuccessBody();
        } finally {
            payNotifyLogMapper.insert(notifyLog);
        }
    }

    @Override
    @Transactional
    public PayResult createPayOrder(String outOrderNo, String channelCode,
                                    BigDecimal totalAmount, Long userId) {
        PayOrder payOrder = PayOrder.builder()
                .orderNo("P" + IdUtil.getSnowflakeNextIdStr())
                .outOrderNo(outOrderNo)
                .userId(userId)
                .channelCode(channelCode)
                .totalAmount(totalAmount)
                .status(PayOrder.STATUS_WAIT)
                .notifyCount(0)
                .build();
        payOrderMapper.insert(payOrder);

        IPayChannelHandler handler = payChannelFactory.getHandler(channelCode);
        PayChannel channel = payChannelMapper.selectOne(
                new LambdaQueryWrapper<PayChannel>().eq(PayChannel::getChannelCode, channelCode));
        PayResult result = handler.unifiedOrder(payOrder, channel);

        // 渠道返回的支付参数（如微信 JSAPI prepay）落库，便于前端唤起支付/排查
        if (result.payParams() != null) {
            payOrder.setPayParams(result.payParams());
            payOrderMapper.updateById(payOrder);
        }

        // 余额/mock 渠道下单即同步入账
        if (result.paid()) {
            markOrderPaid(payOrder, result.tradeNo(), result.buyerId());
        }
        return result;
    }

    @Override
    @Transactional
    public RefundResult refund(Orders order, String reason) {
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOutOrderNo, order.getNumber()));
        if (payOrder == null) {
            return new RefundResult(false, "支付单不存在");
        }
        IPayChannelHandler handler = payChannelFactory.getHandler(payOrder.getChannelCode());
        String refundNo = "R" + IdUtil.getSnowflakeNextIdStr();
        PayChannel channel = payChannelMapper.selectOne(
                new LambdaQueryWrapper<PayChannel>().eq(PayChannel::getChannelCode, payOrder.getChannelCode()));
        return handler.refund(refundNo, order.getPayAmount().toPlainString(), reason, payOrder, channel);
    }

    private int markOrderPaid(PayOrder order, String tradeNo, String buyerId) {
        int updated = payOrderMapper.updateOrderPaid(order.getOrderNo(), tradeNo, buyerId);
        if (updated > 0) {
            sendOrderPaid(order.getOutOrderNo());
        }
        return updated;
    }

    @Override
    public boolean mockConfirmIfWechat(String outOrderNo) {
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOutOrderNo, outOrderNo));
        if (payOrder == null || !"wechat".equals(payOrder.getChannelCode())) {
            return false;
        }
        // 生产 real 模式不自动确认，等真实微信回调
        PayChannel channel = payChannelMapper.selectOne(
                new LambdaQueryWrapper<PayChannel>().eq(PayChannel::getChannelCode, "wechat"));
        if (channel != null && channel.getConfig() != null && !channel.getConfig().isBlank()) {
            try {
                com.alibaba.fastjson2.JSONObject cfg = JSON.parseObject(channel.getConfig());
                if (cfg != null && "real".equalsIgnoreCase(cfg.getString("mode"))) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        // 构造对齐真实微信 JSAPI 回调语义的参数（金额单位分）
        Map<String, String> params = new java.util.HashMap<>();
        params.put("out_trade_no", payOrder.getOrderNo());
        params.put("transaction_id", "WX_MOCK_" + payOrder.getOrderNo());
        params.put("trade_state", "SUCCESS");
        params.put("total", payOrder.getTotalAmount()
                .multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        handleNotify("wechat", params);
        return true;
    }

    private void sendOrderPaid(String outOrderNo) {
        try {
            Message message = new Message(ORDER_PAID_TOPIC,
                    outOrderNo.getBytes(StandardCharsets.UTF_8));
            rocketMQProducer.send(message);
        } catch (Exception e) {
            log.warn("发送支付成功消息失败 outOrderNo={}: {}", outOrderNo, e.getMessage());
        }
    }
}
