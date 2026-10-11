package com.jiwei.trade.pay.channel;

import cn.hutool.core.util.IdUtil;
import com.jiwei.pojo.entity.PayChannel;
import com.jiwei.pojo.entity.PayOrder;
import com.jiwei.trade.pay.core.IPayChannelHandler;
import com.jiwei.trade.pay.core.model.PayNotifyResult;
import com.jiwei.trade.pay.core.model.PayResult;
import com.jiwei.trade.pay.core.model.RefundResult;
import com.jiwei.trade.pay.service.PayAccountService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 余额渠道（下单即扣款入账，同步支付成功）。
 */
@Component
public class BalancePayChannelHandler implements IPayChannelHandler {

    private final PayAccountService payAccountService;

    public BalancePayChannelHandler(PayAccountService payAccountService) {
        this.payAccountService = payAccountService;
    }

    @Override
    public String getChannelCode() {
        return "balance";
    }

    @Override
    public PayResult unifiedOrder(PayOrder order, PayChannel channel) {
        payAccountService.consume(order.getUserId(), order.getTotalAmount(), order.getOrderNo(), "user");
        return PayResult.ok(true, "BALANCE_" + IdUtil.getSnowflakeNextIdStr(), null);
    }

    @Override
    public PayResult queryOrder(PayOrder order, PayChannel channel) {
        return PayResult.ok(true, order.getTradeNo(), null);
    }

    @Override
    public PayNotifyResult parseNotify(Map<String, String> params, PayChannel channel) {
        throw new UnsupportedOperationException("balance 渠道无异步通知");
    }

    @Override
    public boolean closeOrder(PayOrder order, PayChannel channel) {
        return true;
    }

    @Override
    public RefundResult refund(String refundNo, String refundAmount, String reason,
                               PayOrder order, PayChannel channel) {
        payAccountService.refundToAccount(order.getUserId(), new BigDecimal(refundAmount), refundNo);
        return new RefundResult(true, null);
    }

    @Override
    public String notifySuccessBody() {
        return "SUCCESS";
    }

    @Override
    public String notifyFailBody() {
        return "FAIL";
    }
}
