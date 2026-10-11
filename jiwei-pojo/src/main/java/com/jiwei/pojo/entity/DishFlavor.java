package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 菜品口味表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dish_flavor")
public class DishFlavor {
    @TableId(type = IdType.AUTO)
    private Long id;
    //菜品ID
    private Long dishId;
    //口味名称(微辣/中辣/特辣)
    private String name;
    //口味值列表(JSON)
    private String value;
}
