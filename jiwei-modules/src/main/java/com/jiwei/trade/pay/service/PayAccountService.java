package com.jiwei.trade.pay.service;

import java.math.BigDecimal;

/**
 * 余额账户服务（Task 3 补实现）。
 */
public interface PayAccountService {

    void consume(Long userId, BigDecimal amount, String orderNo, String operator);

    void refundToAccount(Long userId, BigDecimal amount, String refundNo);
}
