package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺信息表（多商户）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("merchant_info")
public class MerchantInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    //店铺名称
    private String name;
    //店铺Logo
    private String logo;
    //店铺简介
    private String description;
    //店铺地址
    private String address;
    //经度
    private BigDecimal longitude;
    //纬度
    private BigDecimal latitude;
    //联系电话
    private String phone;
    //营业时间
    private String businessHours;
    //配送范围(米)
    private Integer deliveryRange;
    //状态 0待审核 1营业中 2休息中 3已关闭
    private Integer status;
    //审核驳回原因
    private String auditReason;
    //关联管理员ID(商家账号)
    private Long empId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
