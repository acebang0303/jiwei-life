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
 * 菜品表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dish")
public class Dish {
    @TableId(type = IdType.AUTO)
    private Long id;
    //店铺ID
    private Long merchantId;
    //分类ID
    private Long categoryId;
    //菜品名称
    private String name;
    //图片URL
    private String image;
    //描述
    private String description;
    //价格
    private BigDecimal price;
    //状态 1上架 0下架
    private Integer status;
    //销量
    private Integer sales;
    //菜品语义向量(用于AI向量检索, 存储在pgvector, 非MySQL列)
    @TableField(exist = false)
    private String embedding;
    //菜品口味/规格列表(非表字段, 点餐时选择)
    @TableField(exist = false)
    private List<DishFlavor> flavors;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT)
    private Long createUser;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;
}
