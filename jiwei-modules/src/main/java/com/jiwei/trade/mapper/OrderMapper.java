package com.jiwei.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("trade")
@Mapper
public interface OrderMapper extends BaseMapper<Orders> {

    /**
     * CAS 取消待支付订单：仅当仍处于待支付状态才取消，返回受影响行数。
     * 用于超时关单，避免与支付入账并发时把已支付订单取消。
     */
    @Update("UPDATE orders SET status = 6, cancel_time = NOW(), update_time = NOW() " +
            "WHERE id = #{orderId} AND status = 1")
    int cancelPendingIfUnpaid(@Param("orderId") Long orderId);

    /**
     * 用户主动取消：CAS 仅待支付(1)→已取消(6)，并记录取消原因。返回受影响行数，
     * 避免与支付入账并发时把已支付订单误取消。
     */
    @Update("UPDATE orders SET status = 6, cancel_reason = #{cancelReason}, cancel_time = NOW(), update_time = NOW() " +
            "WHERE id = #{orderId} AND status = 1")
    int cancelByUser(@Param("orderId") Long orderId, @Param("cancelReason") String cancelReason);
}
