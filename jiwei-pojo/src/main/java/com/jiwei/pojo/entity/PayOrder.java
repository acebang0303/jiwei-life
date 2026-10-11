package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付单（幂等 CAS 载体）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pay_order")
public class PayOrder {

    public static final int STATUS_WAIT = 0;      // 待支付
    public static final int STATUS_SUCCESS = 1;   // 已支付
    public static final int STATUS_CLOSED = 2;    // 已关闭

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String outOrderNo;
    private Long userId;
    private String channelCode;
    private BigDecimal totalAmount;
    private Integer status;
    private String tradeNo;
    private String buyerId;
    private LocalDateTime payTime;
    private String payParams;
    private Integer notifyCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
