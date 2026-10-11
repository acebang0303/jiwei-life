package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 权限表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("permission")
public class Permission {
    @TableId(type = IdType.AUTO)
    private Long id;
    //权限名称
    private String name;
    //权限编码(格式: module:action)
    private String code;
    //权限描述
    private String description;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
