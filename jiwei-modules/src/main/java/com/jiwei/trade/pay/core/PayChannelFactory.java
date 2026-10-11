package com.jiwei.trade.pay.core;

import com.jiwei.common.exception.OrderBusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付渠道工厂：构造器注入所有 IPayChannelHandler，按 channelCode 自动注册。
 */
@Component
public class PayChannelFactory {

    private final Map<String, IPayChannelHandler> handlerMap = new ConcurrentHashMap<>();

    public PayChannelFactory(List<IPayChannelHandler> handlers) {
        for (IPayChannelHandler h : handlers) {
            handlerMap.put(h.getChannelCode(), h);
        }
    }

    public IPayChannelHandler getHandler(String channelCode) {
        IPayChannelHandler h = handlerMap.get(channelCode);
        if (h == null) {
            throw new OrderBusinessException("不支持的支付渠道：" + channelCode);
        }
        return h;
    }

    public boolean support(String channelCode) {
        return handlerMap.containsKey(channelCode);
    }
}
