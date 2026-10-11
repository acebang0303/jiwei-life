-- ==========================================
-- 吉味生活 · JiweiLife
-- 数据库: jiwei_auth (认证数据库)
-- ==========================================

CREATE DATABASE IF NOT EXISTS jiwei_auth DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jiwei_auth;

-- 管理员表
CREATE TABLE employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE COMMENT '用户名',
    name VARCHAR(32) NOT NULL COMMENT '姓名',
    password VARCHAR(128) NOT NULL COMMENT '密码(BCrypt加密)',
    phone VARCHAR(11) COMMENT '手机号',
    sex VARCHAR(2) COMMENT '性别',
    id_number VARCHAR(18) COMMENT '身份证号',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    status INT DEFAULT 1 COMMENT '状态 1启用 0禁用',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    create_user BIGINT COMMENT '创建人ID',
    update_user BIGINT COMMENT '更新人ID',
    INDEX idx_username (username)
) COMMENT '管理员表';

-- 角色表
CREATE TABLE role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL COMMENT '角色名称',
    code VARCHAR(32) NOT NULL UNIQUE COMMENT '角色编码(admin/merchant/operator)',
    description VARCHAR(128) COMMENT '角色描述',
    status INT DEFAULT 1,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '角色表';

-- 权限表
CREATE TABLE permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL COMMENT '权限名称',
    code VARCHAR(64) NOT NULL UNIQUE COMMENT '权限编码(格式: module:action)',
    description VARCHAR(128) COMMENT '权限描述',
    create_time DATETIME NOT NULL
) COMMENT '权限表';

-- 角色权限关联表
CREATE TABLE role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    UNIQUE KEY uk_role_permission (role_id, permission_id)
) COMMENT '角色权限关联表';
