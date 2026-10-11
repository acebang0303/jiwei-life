package com.jiwei.trade.pay.service;

import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.pay.core.model.PayResult;
import com.jiwei.trade.pay.core.model.RefundResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付编排服务。
 */
public interface PayOrderService {

    /** 处理渠道回调通知，返回渠道要求的应答体 */
    String handleNotify(String channelCode, Map<String, String> params);

    /** 统一下单：创建支付单 → 渠道下单 → 同步支付成功则入账 */
    PayResult createPayOrder(String outOrderNo, String channelCode, BigDecimal totalAmount, Long userId);

    /** 微信 mock 渠道：发起后自动模拟回调确认入账（开发环境支付闭环，生产 real 模式不生效） */
    boolean mockConfirmIfWechat(String outOrderNo);

    /** 退款：渠道退款成功后回写业务订单状态 */
    RefundResult refund(Orders order, String reason);
}
