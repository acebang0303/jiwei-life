package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 种草笔记表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note")
public class Note {
    @TableId(type = IdType.AUTO)
    private Long id;
    //作者ID
    private Long userId;
    //笔记标题
    private String title;
    //笔记正文
    private String content;
    //图片URL(JSON数组)
    private String images;
    //关联店铺ID
    private Long merchantId;
    //话题标签(JSON数组)
    private String topicTags;
    //发布位置
    private String location;
    //点赞数
    private Integer likeCount;
    //评论数
    private Integer commentCount;
    //收藏数
    private Integer collectCount;
    //浏览数
    private Integer viewCount;
    //审核状态 0待审核 1通过 2驳回
    private Integer auditStatus;
    //是否置顶 1是 0否
    private Integer isTop;
    //笔记语义向量(用于向量检索, 存储在pgvector, 非MySQL列)
    @TableField(exist = false)
    private String embedding;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ===== 非表字段：展示增强 =====
    //作者昵称/头像（feed/详情展示）
    @TableField(exist = false)
    private String nickname;
    @TableField(exist = false)
    private String avatar;
    //当前用户是否已点赞/收藏/关注作者
    @TableField(exist = false)
    private Boolean isLiked;
    @TableField(exist = false)
    private Boolean isCollected;
    @TableField(exist = false)
    private Boolean isFollowing;
    //详情页评论列表
    @TableField(exist = false)
    private List<Comment> comments;
}
