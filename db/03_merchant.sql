-- ==========================================
-- 吉味生活 · JiweiLife
-- 数据库: jiwei_merchant (商家数据库)
-- ==========================================

CREATE DATABASE IF NOT EXISTS jiwei_merchant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jiwei_merchant;

-- 店铺信息表（多商户）
CREATE TABLE merchant_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL COMMENT '店铺名称',
    logo VARCHAR(256) COMMENT '店铺Logo',
    description VARCHAR(512) COMMENT '店铺简介',
    address VARCHAR(256) NOT NULL COMMENT '店铺地址',
    longitude DECIMAL(10,6) COMMENT '经度',
    latitude DECIMAL(10,6) COMMENT '纬度',
    phone VARCHAR(11) COMMENT '联系电话',
    business_hours VARCHAR(64) COMMENT '营业时间',
    delivery_range INT DEFAULT 5000 COMMENT '配送范围(米)',
    status INT DEFAULT 0 COMMENT '状态 0待审核 1营业中 2休息中 3已关闭',
    audit_reason VARCHAR(256) COMMENT '审核驳回原因',
    emp_id BIGINT COMMENT '关联管理员ID(商家账号)',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '店铺信息表';

-- 分类表
CREATE TABLE category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL COMMENT '店铺ID',
    type INT NOT NULL COMMENT '分类类型 1菜品分类 2套餐分类',
    name VARCHAR(32) NOT NULL COMMENT '分类名称',
    sort INT DEFAULT 0 COMMENT '排序',
    status INT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_merchant_id (merchant_id)
) COMMENT '分类表';

-- 菜品表
CREATE TABLE dish (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL COMMENT '店铺ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    name VARCHAR(64) NOT NULL COMMENT '菜品名称',
    image VARCHAR(256) COMMENT '图片URL',
    description VARCHAR(512) COMMENT '描述',
    price DECIMAL(10,2) NOT NULL COMMENT '价格',
    status INT DEFAULT 1 COMMENT '状态 1上架 0下架',
    sales INT DEFAULT 0 COMMENT '销量',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    create_user BIGINT,
    update_user BIGINT,
    INDEX idx_category (category_id),
    INDEX idx_merchant (merchant_id)
) COMMENT '菜品表';

-- 菜品口味表
CREATE TABLE dish_flavor (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    name VARCHAR(32) NOT NULL COMMENT '口味名称(微辣/中辣/特辣)',
    value VARCHAR(256) NOT NULL COMMENT '口味值列表(JSON)',
    INDEX idx_dish_id (dish_id)
) COMMENT '菜品口味表';

-- 套餐表
CREATE TABLE setmeal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL COMMENT '套餐分类ID',
    name VARCHAR(64) NOT NULL COMMENT '套餐名称',
    image VARCHAR(256) COMMENT '图片URL',
    description VARCHAR(512) COMMENT '描述',
    price DECIMAL(10,2) NOT NULL COMMENT '套餐价格',
    status INT DEFAULT 1 COMMENT '状态',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '套餐表';

-- 套餐菜品关联表
CREATE TABLE setmeal_dish (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setmeal_id BIGINT NOT NULL COMMENT '套餐ID',
    dish_id BIGINT NOT NULL COMMENT '菜品ID',
    name VARCHAR(64) COMMENT '菜品名称(冗余)',
    price DECIMAL(10,2) COMMENT '菜品单价(冗余)',
    copies INT DEFAULT 1 COMMENT '份数',
    INDEX idx_setmeal_id (setmeal_id)
) COMMENT '套餐菜品关联表';
