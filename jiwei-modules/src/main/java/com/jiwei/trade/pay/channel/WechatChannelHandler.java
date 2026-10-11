package com.jiwei.trade.pay.channel;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jiwei.pojo.entity.PayChannel;
import com.jiwei.pojo.entity.PayOrder;
import com.jiwei.trade.pay.core.IPayChannelHandler;
import com.jiwei.trade.pay.core.model.PayNotifyResult;
import com.jiwei.trade.pay.core.model.PayResult;
import com.jiwei.trade.pay.core.model.RefundResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信支付 V3 渠道。
 *
 * 开发环境走 mock 模式（pay_channel.config 标记 {"mode":"mock"}，或未配置时默认 mock）：
 * 统一下单返回模拟 JSAPI prepay 参数（paid=false，等待异步回调），
 * 由 /api/mock/wechat/pay-confirm 模拟微信服务器回调完成入账闭环。
 *
 * 生产环境接入真实微信 V3 时，将 config 改为 {"mode":"real"}，
 * 并在各方法内实现 RSA 验签 + AES-GCM 解密（详见 WeChatPayUtil）。
 */
@Component
public class WechatChannelHandler implements IPayChannelHandler {

    @Override
    public String getChannelCode() {
        return "wechat";
    }

    @Override
    public PayResult unifiedOrder(PayOrder order, PayChannel channel) {
        if (!isMock(channel)) {
            return PayResult.fail("微信支付未配置（骨架预留）");
        }
        // mock 模式：模拟 JSAPI 下单，返回 prepay 参数，paid=false 表示等待微信异步回调
        Map<String, String> prepay = new HashMap<>();
        prepay.put("appId", "wx-mock-appid");
        prepay.put("timeStamp", String.valueOf(System.currentTimeMillis() / 1000));
        prepay.put("nonceStr", UUID.randomUUID().toString().replace("-", ""));
        prepay.put("package", "prepay_id=" + order.getOrderNo());
        prepay.put("signType", "RSA");
        prepay.put("paySign", "MOCK_SIGN_" + order.getOrderNo());
        return PayResult.ok(false, "WX_MOCK_" + IdUtil.getSnowflakeNextIdStr(), JSON.toJSONString(prepay));
    }

    @Override
    public PayResult queryOrder(PayOrder order, PayChannel channel) {
        if (!isMock(channel)) {
            return PayResult.fail("微信支付未配置（骨架预留）");
        }
        return PayResult.ok(false, order.getTradeNo(), null);
    }

    @Override
    public PayNotifyResult parseNotify(Map<String, String> params, PayChannel channel) {
        if (!isMock(channel)) {
            throw new UnsupportedOperationException("微信 V3 验签骨架，待接入");
        }
        // mock 模式：参数语义对齐真实微信 JSAPI 回调（out_trade_no / transaction_id / trade_state / total 单位分）
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("transaction_id");
        boolean tradeSuccess = "SUCCESS".equals(params.get("trade_state"));
        BigDecimal payAmount = params.get("total") == null ? null
                : new BigDecimal(params.get("total"))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return new PayNotifyResult(true, tradeSuccess, orderNo, tradeNo, null, payAmount);
    }

    @Override
    public boolean closeOrder(PayOrder order, PayChannel channel) {
        return isMock(channel);
    }

    @Override
    public RefundResult refund(String refundNo, String refundAmount, String reason,
                               PayOrder order, PayChannel channel) {
        if (!isMock(channel)) {
            return new RefundResult(false, "微信退款未配置（骨架预留）");
        }
        return new RefundResult(true, "WX_MOCK_REFUND_" + refundNo);
    }

    @Override
    public String notifySuccessBody() {
        return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";
    }

    @Override
    public String notifyFailBody() {
        return "{\"code\":\"FAIL\",\"message\":\"失败\"}";
    }

    /** 开发环境默认 mock；生产接入时把 config 改为 {"mode":"real"} 走真实微信 V3。 */
    private boolean isMock(PayChannel channel) {
        if (channel == null || channel.getConfig() == null || channel.getConfig().isBlank()) {
            return true;
        }
        JSONObject cfg = JSON.parseObject(channel.getConfig());
        return cfg == null || !"real".equalsIgnoreCase(cfg.getString("mode"));
    }
}
