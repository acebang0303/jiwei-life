package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("orders")
public class Orders {
    //订单状态常量
    public static final Integer PENDING_PAYMENT = 1;      //待支付
    public static final Integer TO_BE_CONFIRMED = 2;      //待接单
    public static final Integer PREPARING = 3;             //备货中
    public static final Integer AWAITING_PICKUP = 4;      //待取餐
    public static final Integer COMPLETED = 5;             //已完成
    public static final Integer CANCELLED = 6;             //已取消
    public static final Integer REFUNDED = 7;              //已退款

    //支付状态常量
    public static final Integer UN_PAID = 0;               //未支付
    public static final Integer PAID = 1;                   //已支付
    public static final Integer REFUND = 2;                 //已退款

    @TableId(type = IdType.AUTO)
    private Long id;
    //订单号
    private String number;
    //用户ID
    private Long userId;
    //店铺ID
    private Long merchantId;
    //地址ID
    private Long addressBookId;
    //地址快照(JSON)
    private String addressDetail;
    //使用的优惠券ID
    private Long userCouponId;
    //订单金额
    private BigDecimal amount;
    //优惠金额
    private BigDecimal discountAmount;
    //配送费
    private BigDecimal deliveryFee;
    //实付金额
    private BigDecimal payAmount;
    //支付方式 1微信支付
    private Integer payMethod;
    //支付状态 0未支付 1已支付 2已退款
    private Integer payStatus;
    //订单状态
    private Integer status;
    //微信支付交易号
    private String transactionId;
    //取消原因
    private String cancelReason;
    //用户备注
    private String remark;
    //是否秒杀订单 1是 0否
    private Integer isSeckill;
    //秒杀活动ID（普通订单为NULL）
    private Long seckillActivityId;
    //预计送达时间
    private LocalDateTime estimatedDeliveryTime;
    //实际送达时间
    private LocalDateTime deliveryTime;
    //支付时间
    private LocalDateTime payTime;
    //取消时间
    private LocalDateTime cancelTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    //非表字段：订单明细（详情接口返回）
    @TableField(exist = false)
    private List<OrderDetail> orderDetails;

    //非表字段：店铺名称（列表展示）
    @TableField(exist = false)
    private String merchantName;
}
