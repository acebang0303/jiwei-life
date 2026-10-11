-- ==========================================
-- 吉味生活 · JiweiLife
-- 数据库: jiwei_social (内容社区数据库)
-- ==========================================

CREATE DATABASE IF NOT EXISTS jiwei_social DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jiwei_social;

-- 评价表
CREATE TABLE review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    dish_id BIGINT COMMENT '菜品ID',
    rating INT NOT NULL COMMENT '评分 1-5星',
    content VARCHAR(1024) COMMENT '评价内容',
    images VARCHAR(2048) COMMENT '图片URL(JSON数组)',
    tags VARCHAR(256) COMMENT '评价标签(JSON数组)',
    is_ai_assisted INT DEFAULT 0 COMMENT '是否AI辅助生成 1是 0否',
    audit_status INT DEFAULT 0 COMMENT '审核状态 0待审核 1通过 2驳回',
    audit_reason VARCHAR(256) COMMENT '审核驳回原因',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    create_time DATETIME NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_dish_id (dish_id)
) COMMENT '评价表';

-- 种草笔记表
CREATE TABLE note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '作者ID',
    title VARCHAR(128) NOT NULL COMMENT '笔记标题',
    content TEXT NOT NULL COMMENT '笔记正文',
    images VARCHAR(2048) COMMENT '图片URL(JSON数组)',
    merchant_id BIGINT COMMENT '关联店铺ID',
    topic_tags VARCHAR(256) COMMENT '话题标签(JSON数组)',
    location VARCHAR(128) COMMENT '发布位置',
    like_count INT DEFAULT 0 COMMENT '点赞数',
    comment_count INT DEFAULT 0 COMMENT '评论数',
    collect_count INT DEFAULT 0 COMMENT '收藏数',
    view_count INT DEFAULT 0 COMMENT '浏览数',
    audit_status INT DEFAULT 0 COMMENT '审核状态 0待审核 1通过 2驳回',
    is_top INT DEFAULT 0 COMMENT '是否置顶 1是 0否',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_merchant (merchant_id),
    INDEX idx_create_time (create_time)
) COMMENT '种草笔记表';

-- 笔记点赞表
CREATE TABLE note_like (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_id BIGINT NOT NULL COMMENT '笔记ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_note_user (note_id, user_id)
) COMMENT '笔记点赞表';

-- 关注表
CREATE TABLE follow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id BIGINT NOT NULL COMMENT '关注者ID',
    followee_id BIGINT NOT NULL COMMENT '被关注者ID',
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_follower_followee (follower_id, followee_id),
    INDEX idx_followee (followee_id)
) COMMENT '关注关系表';

-- 评论表
CREATE TABLE comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    note_id BIGINT NOT NULL COMMENT '笔记ID',
    user_id BIGINT NOT NULL COMMENT '评论者ID',
    parent_id BIGINT DEFAULT NULL COMMENT '父评论ID(支持二级回复)',
    reply_to_user_id BIGINT COMMENT '回复目标用户ID',
    content VARCHAR(512) NOT NULL COMMENT '评论内容',
    like_count INT DEFAULT 0,
    create_time DATETIME NOT NULL,
    INDEX idx_note_id (note_id)
) COMMENT '评论表';
