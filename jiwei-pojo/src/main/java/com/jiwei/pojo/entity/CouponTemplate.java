package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("coupon_template")
public class CouponTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    //优惠券名称
    private String name;
    //类型 1满减券 2折扣券 3现金券
    private Integer type;
    //使用门槛(满多少可用)
    private BigDecimal threshold;
    //优惠值(金额或折扣)
    private BigDecimal discountValue;
    //发放总量, 0不限量
    private Integer totalCount;
    //每人限领数量
    private Integer perUserLimit;
    //有效期(自领取起X天)
    private Integer validDays;
    //状态 1启用 0禁用
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    //非表字段：当前用户已领取数量
    @TableField(exist = false)
    private Integer receivedCount;
}
