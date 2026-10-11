package com.jiwei.trade.pay.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@DS("trade")
@Mapper
public interface PayAccountMapper extends BaseMapper<PayAccount> {

    /** 条件扣减：balance >= amount 防止超扣，status=0 仅正常账户 */
    @Update("UPDATE pay_account SET balance = balance - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND status = 0 AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 加余额 */
    @Update("UPDATE pay_account SET balance = balance + #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND status = 0")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
