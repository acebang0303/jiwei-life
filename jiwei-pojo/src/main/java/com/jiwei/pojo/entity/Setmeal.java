package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 套餐表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("setmeal")
public class Setmeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    //店铺ID
    private Long merchantId;
    //套餐分类ID
    private Long categoryId;
    //套餐名称
    private String name;
    //图片URL
    private String image;
    //描述
    private String description;
    //套餐价格
    private BigDecimal price;
    //状态 1启用 0禁用
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
