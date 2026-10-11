package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 支付回调留痕
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pay_notify_log")
public class PayNotifyLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String channelCode;
    private String orderNo;
    private Integer notifyType;
    private String content;
    private Integer verifyStatus;
    private Integer processStatus;
    private String processMsg;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
