package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户行为记录表（用于 AI 个性化推荐）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_behavior")
public class UserBehavior {
    @TableId(type = IdType.AUTO)
    private Long id;
    //用户ID
    private Long userId;
    //行为类型 LIKE_NOTE/COLLECT_NOTE/COMMENT_NOTE/VIEW_MERCHANT
    private String type;
    //目标ID（笔记ID或店铺ID）
    private Long targetId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
