package com.jiwei.trade.pay.channel;

import cn.hutool.core.util.IdUtil;
import com.jiwei.pojo.entity.PayChannel;
import com.jiwei.pojo.entity.PayOrder;
import com.jiwei.trade.pay.core.IPayChannelHandler;
import com.jiwei.trade.pay.core.model.PayNotifyResult;
import com.jiwei.trade.pay.core.model.PayResult;
import com.jiwei.trade.pay.core.model.RefundResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mock 渠道（开发闭环，下单即支付成功）。
 */
@Component
public class MockChannelHandler implements IPayChannelHandler {

    @Override
    public String getChannelCode() {
        return "mock";
    }

    @Override
    public PayResult unifiedOrder(PayOrder order, PayChannel channel) {
        return PayResult.ok(true, "MOCK_" + IdUtil.getSnowflakeNextIdStr(), null);
    }

    @Override
    public PayResult queryOrder(PayOrder order, PayChannel channel) {
        return PayResult.ok(true, order.getTradeNo(), null);
    }

    @Override
    public PayNotifyResult parseNotify(Map<String, String> params, PayChannel channel) {
        throw new UnsupportedOperationException("mock 渠道无异步通知");
    }

    @Override
    public boolean closeOrder(PayOrder order, PayChannel channel) {
        return true;
    }

    @Override
    public RefundResult refund(String refundNo, String refundAmount, String reason,
                               PayOrder order, PayChannel channel) {
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
