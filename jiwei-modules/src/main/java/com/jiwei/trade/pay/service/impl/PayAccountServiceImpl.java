package com.jiwei.trade.pay.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.pojo.entity.PayAccount;
import com.jiwei.pojo.entity.PayAccountTransaction;
import com.jiwei.trade.pay.mapper.PayAccountMapper;
import com.jiwei.trade.pay.mapper.PayAccountTransactionMapper;
import com.jiwei.trade.pay.service.PayAccountService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 余额账户服务：条件扣减 + 流水同事务幂等。
 */
@DS("trade")
@Service
public class PayAccountServiceImpl implements PayAccountService {

    private final PayAccountMapper accountMapper;
    private final PayAccountTransactionMapper transactionMapper;

    public PayAccountServiceImpl(PayAccountMapper accountMapper,
                                 PayAccountTransactionMapper transactionMapper) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
    }

    @Override
    @Transactional
    public void consume(Long userId, BigDecimal amount, String orderNo, String operator) {
        PayAccount account = getOrCreateAccount(userId);
        if (account.getStatus() != null && account.getStatus() != 0) {
            throw new OrderBusinessException("账户已冻结，无法使用余额支付");
        }
        int updated = accountMapper.deductBalance(userId, amount);
        if (updated == 0) {
            throw new OrderBusinessException("账户余额不足");
        }
        try {
            insertTransaction(userId, PayAccountTransaction.TRANS_TYPE_CONSUME,
                    amount.negate(), orderNo, "余额支付订单 [" + orderNo + "]", operator);
        } catch (DuplicateKeyException e) {
            throw new OrderBusinessException("订单 [" + orderNo + "] 已扣款，请勿重复支付");
        }
    }

    @Override
    @Transactional
    public void refundToAccount(Long userId, BigDecimal amount, String refundNo) {
        PayAccountTransaction exists = transactionMapper.selectByTypeAndBizNo(
                PayAccountTransaction.TRANS_TYPE_REFUND, refundNo);
        if (exists != null) {
            return;
        }
        accountMapper.addBalance(userId, amount);
        try {
            insertTransaction(userId, PayAccountTransaction.TRANS_TYPE_REFUND,
                    amount, refundNo, "退款单 [" + refundNo + "] 退回余额", "system");
        } catch (DuplicateKeyException ignored) {
            // 并发重复退款，唯一键兜底，静默返回
        }
    }

    private PayAccount getOrCreateAccount(Long userId) {
        PayAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        if (account != null) {
            return account;
        }
        try {
            PayAccount insert = PayAccount.builder()
                    .userId(userId).balance(BigDecimal.ZERO).status(0).build();
            accountMapper.insert(insert);
            return accountMapper.selectOne(
                    new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        } catch (DuplicateKeyException e) {
            return accountMapper.selectOne(
                    new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        }
    }

    private void insertTransaction(Long userId, int transType, BigDecimal amount,
                                   String bizNo, String remark, String operator) {
        PayAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        PayAccountTransaction t = PayAccountTransaction.builder()
                .transNo("B" + IdUtil.getSnowflakeNextIdStr())
                .userId(userId).transType(transType).amount(amount)
                .balanceAfter(account.getBalance()).bizNo(bizNo)
                .remark(remark).build();
        transactionMapper.insert(t);
    }
}
