package com.jiwei.trade.pay.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.result.Result;
import com.jiwei.pojo.entity.PayOrder;
import com.jiwei.trade.pay.mapper.PayOrderMapper;
import com.jiwei.trade.pay.service.PayOrderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 开发环境专用：模拟微信服务器支付异步回调（mock 模式）。
 * 生产环境由微信服务器 POST 真实回调到 /api/notify/pay/wechat，此接口不存在。
 */
@RestController
@RequestMapping("/api/mock/wechat")
public class MockWechatPayConfirmController {

    private final PayOrderService payOrderService;
    private final PayOrderMapper payOrderMapper;

    public MockWechatPayConfirmController(PayOrderService payOrderService, PayOrderMapper payOrderMapper) {
        this.payOrderService = payOrderService;
        this.payOrderMapper = payOrderMapper;
    }

    /**
     * 模拟微信支付成功回调：按业务订单号（orders.number）确认支付单入账。
     */
    @PostMapping("/pay-confirm")
    public Result<String> confirm(@RequestParam String outOrderNo) {
        PayOrder payOrder = payOrderMapper.selectOne(
                new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOutOrderNo, outOrderNo));
        if (payOrder == null) {
            return Result.error("支付单不存在: " + outOrderNo);
        }
        // 构造对齐真实微信 JSAPI 回调语义的参数（金额单位分）
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", payOrder.getOrderNo());
        params.put("transaction_id", "WX_MOCK_" + payOrder.getOrderNo());
        params.put("trade_state", "SUCCESS");
        params.put("total", payOrder.getTotalAmount()
                .multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        return Result.success(payOrderService.handleNotify("wechat", params));
    }
}
