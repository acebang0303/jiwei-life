package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 评价表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    //用户ID
    private Long userId;
    //订单ID
    private Long orderId;
    //菜品ID
    private Long dishId;
    //评分 1-5星
    private Integer rating;
    //评价内容
    private String content;
    //图片URL(JSON数组)
    private String images;
    //评价标签(JSON数组)
    private String tags;
    //是否AI辅助生成 1是 0否
    private Integer isAiAssisted;
    //审核状态 0待审核 1通过 2驳回
    private Integer auditStatus;
    //审核驳回原因
    private String auditReason;
    //点赞数
    private Integer likeCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
