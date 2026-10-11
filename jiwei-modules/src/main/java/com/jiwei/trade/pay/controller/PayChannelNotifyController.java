package com.jiwei.trade.pay.controller;

import com.jiwei.trade.pay.core.PayChannelFactory;
import com.jiwei.trade.pay.service.PayOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 渠道回调统一入口：委托 PayOrderService.handleNotify 幂等入账。
 */
@RestController
@RequestMapping("/api/notify/pay")
public class PayChannelNotifyController {

    private final PayOrderService payOrderService;
    private final PayChannelFactory factory;

    public PayChannelNotifyController(PayOrderService payOrderService, PayChannelFactory factory) {
        this.payOrderService = payOrderService;
        this.factory = factory;
    }

    @PostMapping("/{channelCode}")
    public String receiveNotify(@PathVariable String channelCode,
                                @RequestParam Map<String, String> params) {
        if (!factory.support(channelCode)) {
            return "fail";
        }
        return payOrderService.handleNotify(channelCode, params);
    }
}
