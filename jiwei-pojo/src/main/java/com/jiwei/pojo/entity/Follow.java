package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 关注关系表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("follow")
public class Follow {
    @TableId(type = IdType.AUTO)
    private Long id;
    //关注者ID
    private Long followerId;
    //被关注者ID
    private Long followeeId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
