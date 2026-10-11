package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户优惠券表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO)
    private Long id;
    //模板ID
    private Long templateId;
    //用户ID
    private Long userId;
    //状态 0未使用 1已使用 2已过期 3已锁定(下单中)
    private Integer status;
    //使用的订单ID
    private Long orderId;
    //领取时间
    private LocalDateTime receiveTime;
    //使用时间
    private LocalDateTime useTime;
    //过期时间
    private LocalDateTime expireTime;
    //非表字段：关联的优惠券模板（列表展示券面值/名称）
    @TableField(exist = false)
    private CouponTemplate template;
}
