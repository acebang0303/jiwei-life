package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 笔记点赞表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_like")
public class NoteLike {
    @TableId(type = IdType.AUTO)
    private Long id;
    //笔记ID
    private Long noteId;
    //用户ID
    private Long userId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
