package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额流水（uk_type_biz 唯一键防重）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pay_account_transaction")
public class PayAccountTransaction {

    public static final int TRANS_TYPE_CONSUME = 1; // 消费（余额支付）
    public static final int TRANS_TYPE_REFUND = 2;  // 退款退回
    public static final int TRANS_TYPE_ADJUST = 3;  // 后台调整

    @TableId(type = IdType.AUTO)
    private Long id;
    private String transNo;
    private Long userId;
    private Integer transType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String bizNo;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
