package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 短链
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("short_link")
public class ShortLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String shortCode;
    private String longUrl;
    private Long urlHash;
    private Long clickCount;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
