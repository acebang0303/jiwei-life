package com.jiwei.trade.pay.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayAccountTransaction;
import org.apache.ibatis.annotations.Mapper;

@DS("trade")
@Mapper
public interface PayAccountTransactionMapper extends BaseMapper<PayAccountTransaction> {

    default PayAccountTransaction selectByTypeAndBizNo(int transType, String bizNo) {
        return selectOne(new LambdaQueryWrapper<PayAccountTransaction>()
                .eq(PayAccountTransaction::getTransType, transType)
                .eq(PayAccountTransaction::getBizNo, bizNo));
    }
}
