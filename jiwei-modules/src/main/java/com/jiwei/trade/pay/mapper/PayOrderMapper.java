package com.jiwei.trade.pay.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@DS("trade")
@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /** CAS 入账：status='0' 前置条件保证并发/重复回调只生效一次 */
    @Update("UPDATE pay_order SET status = 1, trade_no = #{tradeNo}, " +
            "buyer_id = #{buyerId}, pay_time = NOW(), update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND status = 0")
    int updateOrderPaid(@Param("orderNo") String orderNo,
                        @Param("tradeNo") String tradeNo,
                        @Param("buyerId") String buyerId);

    /** 关闭订单：仅待支付可关（CAS） */
    @Update("UPDATE pay_order SET status = 2, update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND status = 0")
    int updateOrderClosed(@Param("orderNo") String orderNo);

    /** 回调计数 +1 */
    @Update("UPDATE pay_order SET notify_count = notify_count + 1 WHERE order_no = #{orderNo}")
    int increaseNotifyCount(@Param("orderNo") String orderNo);
}
