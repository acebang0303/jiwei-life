package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("seckill_activity")
public class SeckillActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    //活动名称
    private String name;
    //秒杀菜品ID
    private Long dishId;
    //秒杀价格
    private BigDecimal seckillPrice;
    //秒杀库存
    private Integer stock;
    //已售数量
    private Integer sold;
    //每人限购数量
    private Integer limitPerUser;
    //开始时间
    private LocalDateTime startTime;
    //结束时间
    private LocalDateTime endTime;
    //状态 0未开始 1进行中 2已结束
    private Integer status;
    //乐观锁版本号
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    //非表字段：菜品名/店铺名（列表展示）
    @TableField(exist = false)
    private String dishName;
    @TableField(exist = false)
    private String merchantName;
}
