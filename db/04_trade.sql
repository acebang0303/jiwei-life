-- ==========================================
-- 吉味生活 · JiweiLife
-- 数据库: jiwei_trade (交易数据库)
-- ==========================================

CREATE DATABASE IF NOT EXISTS jiwei_trade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE jiwei_trade;

-- 购物车表(MySQL兜底, 主存储为Redis)
CREATE TABLE shopping_cart (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    dish_id BIGINT COMMENT '菜品ID',
    setmeal_id BIGINT COMMENT '套餐ID',
    dish_flavor VARCHAR(256) COMMENT '口味',
    name VARCHAR(64) COMMENT '名称',
    image VARCHAR(256) COMMENT '图片',
    amount DECIMAL(10,2) COMMENT '单价',
    number INT DEFAULT 1 COMMENT '数量',
    create_time DATETIME NOT NULL,
    INDEX idx_user_id (user_id)
) COMMENT '购物车表';

-- 订单表
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    number VARCHAR(64) NOT NULL UNIQUE COMMENT '订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    merchant_id BIGINT NOT NULL COMMENT '店铺ID',
    address_book_id BIGINT NOT NULL COMMENT '地址ID',
    address_detail VARCHAR(512) COMMENT '地址快照(冗余)',
    user_coupon_id BIGINT COMMENT '使用的优惠券ID',
    amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    discount_amount DECIMAL(10,2) DEFAULT 0 COMMENT '优惠金额',
    delivery_fee DECIMAL(10,2) DEFAULT 0 COMMENT '配送费',
    pay_amount DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    pay_method INT COMMENT '支付方式 1微信支付',
    pay_status INT DEFAULT 0 COMMENT '支付状态 0未支付 1已支付 2已退款',
    status INT DEFAULT 1 COMMENT '订单状态 1待支付 2待接单 3备货中 4待取餐 5已完成 6已取消 7已退款',
    transaction_id VARCHAR(64) COMMENT '微信支付交易号',
    cancel_reason VARCHAR(256) COMMENT '取消原因',
    remark VARCHAR(256) COMMENT '用户备注',
    is_seckill INT DEFAULT 0 COMMENT '是否秒杀订单 1是 0否',
    seckill_activity_id BIGINT COMMENT '秒杀活动ID(普通订单NULL)',
    estimated_delivery_time DATETIME COMMENT '预计送达时间',
    delivery_time DATETIME COMMENT '实际送达时间',
    pay_time DATETIME COMMENT '支付时间',
    cancel_time DATETIME COMMENT '取消时间',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_number (number),
    INDEX idx_status (status),
    UNIQUE KEY uk_user_activity (user_id, seckill_activity_id)
) COMMENT '订单表';

-- 订单明细表
CREATE TABLE order_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL COMMENT '订单ID',
    name VARCHAR(64) COMMENT '菜品/套餐名称',
    image VARCHAR(256) COMMENT '图片',
    dish_flavor VARCHAR(256) COMMENT '口味',
    amount DECIMAL(10,2) COMMENT '单价',
    number INT DEFAULT 1 COMMENT '数量',
    INDEX idx_order_id (order_id)
) COMMENT '订单明细表';

-- ============ 支付中台 ============

-- 支付渠道配置表
CREATE TABLE pay_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(32) NOT NULL UNIQUE COMMENT '渠道编码 balance/mock/wechat',
    channel_name VARCHAR(64) NOT NULL COMMENT '渠道名称',
    status INT DEFAULT 0 COMMENT '状态 0启用 1停用',
    config TEXT COMMENT '渠道配置JSON(密钥/网关等)',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '支付渠道配置表';

-- 支付单表（幂等 CAS 载体）
CREATE TABLE pay_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '支付单号',
    out_order_no VARCHAR(64) NOT NULL COMMENT '业务订单号(orders.number)',
    user_id BIGINT NOT NULL COMMENT '下单用户ID(余额扣款用)',
    channel_code VARCHAR(32) NOT NULL COMMENT '渠道编码',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status INT DEFAULT 0 COMMENT '状态 0待支付 1已支付 2已关闭',
    trade_no VARCHAR(64) COMMENT '渠道交易号',
    buyer_id VARCHAR(64) COMMENT '买家渠道账号',
    pay_time DATETIME COMMENT '支付完成时间',
    pay_params TEXT COMMENT '下单返回的支付参数',
    notify_count INT DEFAULT 0 COMMENT '回调通知次数',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_out_order_no (out_order_no),
    INDEX idx_status (status)
) COMMENT '支付单表';

-- 支付回调留痕表
CREATE TABLE pay_notify_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(32) NOT NULL,
    order_no VARCHAR(64),
    notify_type INT DEFAULT 1 COMMENT '1支付 2退款',
    content TEXT COMMENT '原始通知内容',
    verify_status INT DEFAULT 0 COMMENT '0未验 1成功 2失败',
    process_status INT DEFAULT 0 COMMENT '0未处理 1成功 2失败',
    process_msg VARCHAR(512) COMMENT '处理结果说明',
    create_time DATETIME NOT NULL
) COMMENT '支付回调留痕表';

-- 余额账户表
CREATE TABLE pay_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    balance DECIMAL(10,2) DEFAULT 0 NOT NULL COMMENT '余额',
    status INT DEFAULT 0 COMMENT '状态 0正常 1冻结',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '余额账户表';

-- 余额流水表（uk_type_biz 唯一键防重）
CREATE TABLE pay_account_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trans_no VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    user_id BIGINT NOT NULL,
    trans_type INT NOT NULL COMMENT '1消费 2退款 3调整',
    amount DECIMAL(10,2) NOT NULL COMMENT '变动金额(正加负减)',
    balance_after DECIMAL(10,2) NOT NULL COMMENT '变动后余额',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号(订单号/退款号)',
    remark VARCHAR(256),
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_type_biz (trans_type, biz_no),
    INDEX idx_user_id (user_id)
) COMMENT '余额流水表';
