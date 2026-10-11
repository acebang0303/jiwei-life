package com.jiwei.trade.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * 订单提交请求DTO
 */
@Data
public class OrderSubmitDTO implements Serializable {
    //店铺ID
    private Long merchantId;
    //收货地址ID
    private Long addressBookId;
    //支付方式 1微信支付
    private Integer payMethod;
    //使用的优惠券ID
    private Long userCouponId;
    //用户备注
    private String remark;
}
