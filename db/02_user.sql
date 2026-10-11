-- ==========================================
-- 吉味生活 · JiweiLife
-- 数据库: jiwei_user (用户数据库)
-- ==========================================

CREATE DATABASE IF NOT EXISTS jiwei_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jiwei_user;

-- C端用户表
CREATE TABLE user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid VARCHAR(64) NOT NULL UNIQUE COMMENT '微信openid',
    nickname VARCHAR(32) COMMENT '用户昵称',
    avatar VARCHAR(256) COMMENT '头像URL',
    phone VARCHAR(11) COMMENT '手机号',
    sex INT DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    growth_value INT DEFAULT 0 COMMENT '成长值',
    level INT DEFAULT 1 COMMENT '用户等级 1-6',
    preference_tags VARCHAR(512) COMMENT 'AI偏好标签(JSON数组)',
    status INT DEFAULT 1 COMMENT '状态 1正常 0禁用',
    create_time DATETIME NOT NULL COMMENT '注册时间',
    update_time DATETIME NOT NULL
) COMMENT '用户表';

-- 收货地址表
CREATE TABLE address_book (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    consignee VARCHAR(32) NOT NULL COMMENT '收货人',
    phone VARCHAR(11) NOT NULL COMMENT '手机号',
    sex VARCHAR(2) COMMENT '性别',
    province_code VARCHAR(12) COMMENT '省份编码',
    province_name VARCHAR(32) COMMENT '省份名称',
    city_code VARCHAR(12) COMMENT '城市编码',
    city_name VARCHAR(32) COMMENT '城市名称',
    district_code VARCHAR(12) COMMENT '区县编码',
    district_name VARCHAR(32) COMMENT '区县名称',
    detail VARCHAR(256) COMMENT '详细地址',
    label VARCHAR(32) COMMENT '标签(家/公司/学校)',
    is_default INT DEFAULT 0 COMMENT '是否默认 1是 0否',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_user_id (user_id)
) COMMENT '收货地址表';
