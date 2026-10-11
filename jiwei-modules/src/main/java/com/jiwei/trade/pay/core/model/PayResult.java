package com.jiwei.trade.pay.core.model;

/**
 * 统一下单结果。
 */
public record PayResult(boolean success, boolean paid, String tradeNo,
                        String buyerId, String payParams, String message) {

    public static PayResult ok(boolean paid, String tradeNo, String payParams) {
        return new PayResult(true, paid, tradeNo, null, payParams, null);
    }

    public static PayResult fail(String message) {
        return new PayResult(false, false, null, null, null, message);
    }
}
