-- ==========================================
-- 11_fix_address_book.sql: 修复 mock 订单收货地址悬空
-- 98_mock_data 的订单把 address_book_id 直接填成 user_id(100-400)，
-- 但 address_book 表只有 seed 的 5 行。本脚本为 mock 用户补齐地址，
-- 使订单详情回查地址有真实数据。幂等，可重复执行。
-- ==========================================

INSERT IGNORE INTO jiwei_user.address_book
    (id, user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code, district_name, detail, label, is_default, create_time, update_time)
SELECT
    u.id AS id,
    u.id AS user_id,
    CONCAT('用户', u.id) AS consignee,
    u.phone AS phone,
    1 AS sex,
    '330000' AS province_code, '浙江省' AS province_name,
    '330100' AS city_code, '杭州市' AS city_name,
    '330102' AS district_code, '上城区' AS district_name,
    CONCAT('演示路', (u.id % 100) + 1, '号') AS detail,
    '家' AS label,
    1 AS is_default,
    NOW() AS create_time,
    NOW() AS update_time
FROM jiwei_user.user u
WHERE u.id BETWEEN 100 AND 400
AND NOT EXISTS (SELECT 1 FROM jiwei_user.address_book ab WHERE ab.id = u.id);
