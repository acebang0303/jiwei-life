package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * C端用户表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    //微信openid
    private String openid;
    //用户昵称
    private String nickname;
    //头像URL
    private String avatar;
    //手机号
    private String phone;
    //性别 0未知 1男 2女
    private Integer sex;
    //成长值
    private Integer growthValue;
    //用户等级 1-6
    private Integer level;
    //AI偏好标签(JSON数组)
    private String preferenceTags;
    //状态 1正常 0禁用
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
