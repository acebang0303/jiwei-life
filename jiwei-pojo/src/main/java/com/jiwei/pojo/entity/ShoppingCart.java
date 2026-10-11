package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车表(MySQL兜底, 主存储为Redis)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("shopping_cart")
public class ShoppingCart {
    @TableId(type = IdType.AUTO)
    private Long id;
    //用户ID
    private Long userId;
    //店铺ID
    private Long merchantId;
    //菜品ID
    private Long dishId;
    //套餐ID
    private Long setmealId;
    //口味
    private String dishFlavor;
    //名称
    private String name;
    //图片
    private String image;
    //单价
    private BigDecimal amount;
    //数量
    private Integer number;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
