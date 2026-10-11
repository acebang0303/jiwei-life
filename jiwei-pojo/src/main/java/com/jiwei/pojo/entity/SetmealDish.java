package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * 套餐菜品关联表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("setmeal_dish")
public class SetmealDish {
    @TableId(type = IdType.AUTO)
    private Long id;
    //套餐ID
    private Long setmealId;
    //菜品ID
    private Long dishId;
    //菜品名称(冗余)
    private String name;
    //菜品单价(冗余)
    private BigDecimal price;
    //份数
    private Integer copies;
}
