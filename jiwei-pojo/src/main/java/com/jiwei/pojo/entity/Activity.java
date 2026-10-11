package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 首页活动表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("activity")
public class Activity {
    @TableId(type = IdType.AUTO)
    private Long id;
    //活动标题
    private String title;
    //活动副标题
    private String subtitle;
    //banner 背景渐变
    private String bgColor;
    //跳转类型 1=店铺 2=秒杀 3=优惠券 4=笔记详情
    private Integer type;
    //跳转目标ID
    private Long targetId;
    //排序
    private Integer sort;
    //状态 1=上架 0=下架
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
