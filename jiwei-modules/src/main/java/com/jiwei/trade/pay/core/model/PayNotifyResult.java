package com.jiwei.trade.pay.core.model;

import java.math.BigDecimal;

/**
 * 回调通知解析结果。
 */
public record PayNotifyResult(boolean verifySuccess, boolean tradeSuccess,
                              String orderNo, String tradeNo, String failMsg,
                              BigDecimal payAmount) {
}
