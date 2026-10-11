-- ==========================================
-- 吉味生活 · JiweiLife 示例种子数据
-- ==========================================

-- ==========================================
-- jiwei_auth: 扩充管理员
-- ==========================================
INSERT IGNORE INTO jiwei_auth.employee (id, username, name, password, phone, role_id, status, create_time, update_time) VALUES
(1, 'admin', '管理员', '$2a$10$la.k3NfhRPBlkv5UqClbe.Hq.9m4IlCylz1mOe2YHxNU7YpcJ2AXO', '13800138001', 1, 1, NOW(), NOW()),
(2, 'merchant01', '张记面馆', '$2a$10$la.k3NfhRPBlkv5UqClbe.Hq.9m4IlCylz1mOe2YHxNU7YpcJ2AXO', '13800138002', 2, 1, NOW(), NOW()),
(3, 'merchant02', '老王烧烤', '$2a$10$la.k3NfhRPBlkv5UqClbe.Hq.9m4IlCylz1mOe2YHxNU7YpcJ2AXO', '13800138003', 2, 1, NOW(), NOW()),
(4, 'merchant03', '蜀味川菜', '$2a$10$la.k3NfhRPBlkv5UqClbe.Hq.9m4IlCylz1mOe2YHxNU7YpcJ2AXO', '13800138004', 2, 1, NOW(), NOW()),
(5, 'merchant04', '外婆家私房菜', '$2a$10$la.k3NfhRPBlkv5UqClbe.Hq.9m4IlCylz1mOe2YHxNU7YpcJ2AXO', '13800138005', 2, 1, NOW(), NOW()),
(6, 'operator01', '运营小王', '$2a$10$la.k3NfhRPBlkv5UqClbe.Hq.9m4IlCylz1mOe2YHxNU7YpcJ2AXO', '13800138006', 3, 1, NOW(), NOW());

INSERT IGNORE INTO jiwei_auth.role (id, name, code, description, status, create_time, update_time) VALUES
(1, '超级管理员', 'admin', '平台管理员，拥有全部权限', 1, NOW(), NOW()),
(2, '商家', 'merchant', '入驻商家账号', 1, NOW(), NOW()),
(3, '运营', 'operator', '内容审核与运营', 1, NOW(), NOW());

INSERT IGNORE INTO jiwei_auth.permission (id, name, code, description, create_time) VALUES
(1, '员工管理', 'employee:manage', '增删改查员工', NOW()),
(2, '商户审核', 'merchant:audit', '审核商户入驻', NOW()),
(3, '菜品管理', 'dish:manage', '菜品CRUD', NOW()),
(4, '订单管理', 'order:manage', '订单查询处理', NOW()),
(5, '营销管理', 'market:manage', '秒杀优惠券管理', NOW()),
(6, '内容审核', 'content:audit', '评价笔记审核', NOW()),
(7, '数据统计', 'statistics:view', '查看统计报表', NOW());

INSERT IGNORE INTO jiwei_auth.role_permission (role_id, permission_id) VALUES
(1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),
(2,3),(2,4),(2,5),(2,7),
(3,1),(3,2),(3,6),(3,7);

-- ==========================================
-- jiwei_user: C端用户
-- ==========================================
INSERT INTO jiwei_user.user (id, openid, nickname, avatar, phone, sex, growth_value, level, preference_tags, status, create_time, update_time) VALUES
(1, 'mock_openid_001', '吃货小陈', 'https://api.dicebear.com/7.x/avataaars/svg?seed=chen', '13900000001', 1, 850, 3, '["火锅","川菜","深夜食堂"]', 1, NOW(), NOW()),
(2, 'mock_openid_002', '甜品控小美', 'https://api.dicebear.com/7.x/avataaars/svg?seed=mei', '13900000002', 2, 420, 2, '["甜品","奶茶","轻食"]', 1, NOW(), NOW()),
(3, 'mock_openid_003', '老饕李哥', 'https://api.dicebear.com/7.x/avataaars/svg?seed=li', '13900000003', 1, 2100, 5, '["烧烤","海鲜","啤酒"]', 1, NOW(), NOW()),
(4, 'mock_openid_004', '加班小王', 'https://api.dicebear.com/7.x/avataaars/svg?seed=wang', '13900000004', 1, 180, 1, '["快餐","面食"]', 1, NOW(), NOW()),
(5, 'mock_openid_005', '健身达人Amy', 'https://api.dicebear.com/7.x/avataaars/svg?seed=amy', '13900000005', 2, 650, 3, '["轻食","沙拉","果汁"]', 1, NOW(), NOW());

INSERT INTO jiwei_user.address_book (user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code, district_name, detail, label, is_default, create_time, update_time) VALUES
(1, '陈先生', '13900000001', '1', '330000', '浙江省', '330100', '杭州市', '330102', '上城区', '延安路258号湖滨银泰A座1206', '家', 1, NOW(), NOW()),
(1, '陈先生', '13900000001', '1', '330000', '浙江省', '330100', '杭州市', '330106', '西湖区', '文三路478号华星时代广场', '公司', 0, NOW(), NOW()),
(2, '小美', '13900000002', '0', '330000', '浙江省', '330100', '杭州市', '330108', '滨江区', '江南大道228号星光大道3幢1502', '家', 1, NOW(), NOW()),
(3, '李哥', '13900000003', '1', '330000', '浙江省', '330100', '杭州市', '330103', '下城区', '武林路168号', '家', 1, NOW(), NOW()),
(4, '王先生', '13900000004', '1', '330000', '浙江省', '330100', '杭州市', '330110', '余杭区', '文一西路998号海创园5号楼', '公司', 1, NOW(), NOW());

-- ==========================================
-- jiwei_merchant: 商户、分类、菜品
-- ==========================================
INSERT INTO jiwei_merchant.merchant_info (id, name, logo, description, address, longitude, latitude, phone, business_hours, delivery_range, status, emp_id, create_time, update_time) VALUES
(1, '张记面馆', 'https://api.dicebear.com/7.x/icons/svg?seed=noodle', '三代传承的老字号面馆，手工拉面，每日现熬骨汤', '杭州市上城区中山南路168号', 120.168, 30.241, '13800138002', '06:00-21:00', 5000, 1, 2, NOW(), NOW()),
(2, '老王烧烤', 'https://api.dicebear.com/7.x/icons/svg?seed=bbq', '深夜食堂首选！二十年烧烤老店，秘制酱料', '杭州市拱墅区莫干山路388号', 120.152, 30.289, '13800138003', '17:00-02:00', 3000, 1, 3, NOW(), NOW()),
(3, '蜀味川菜', 'https://api.dicebear.com/7.x/icons/svg?seed=spicy', '正宗川味，麻辣鲜香！成都师傅掌勺', '杭州市西湖区古墩路128号', 120.135, 30.265, '13800138004', '10:00-22:00', 5000, 1, 4, NOW(), NOW()),
(4, '外婆家私房菜', 'https://api.dicebear.com/7.x/icons/svg?seed=home', '家的味道，每日新鲜食材，健康家常菜', '杭州市滨江区江南大道999号', 120.212, 30.208, '13800138005', '09:00-21:00', 4000, 1, 5, NOW(), NOW());

-- 张记面馆 分类
INSERT INTO jiwei_merchant.category (id, merchant_id, type, name, sort, status, create_time, update_time) VALUES
(1, 1, 1, '招牌面', 1, 1, NOW(), NOW()),
(2, 1, 1, '盖浇饭', 2, 1, NOW(), NOW()),
(3, 1, 1, '小食凉菜', 3, 1, NOW(), NOW()),
(4, 1, 2, '超值套餐', 1, 1, NOW(), NOW());

-- 老王烧烤 分类
INSERT INTO jiwei_merchant.category (id, merchant_id, type, name, sort, status, create_time, update_time) VALUES
(5, 2, 1, '烤串', 1, 1, NOW(), NOW()),
(6, 2, 1, '海鲜', 2, 1, NOW(), NOW()),
(7, 2, 1, '酒水饮品', 3, 1, NOW(), NOW()),
(8, 2, 2, '畅饮套餐', 1, 1, NOW(), NOW());

-- 蜀味川菜 分类
INSERT INTO jiwei_merchant.category (id, merchant_id, type, name, sort, status, create_time, update_time) VALUES
(9, 3, 1, '经典川菜', 1, 1, NOW(), NOW()),
(10, 3, 1, '特色干锅', 2, 1, NOW(), NOW()),
(11, 3, 1, '主食', 3, 1, NOW(), NOW()),
(12, 3, 2, '聚会套餐', 1, 1, NOW(), NOW());

-- 外婆家 分类
INSERT INTO jiwei_merchant.category (id, merchant_id, type, name, sort, status, create_time, update_time) VALUES
(13, 4, 1, '热菜', 1, 1, NOW(), NOW()),
(14, 4, 1, '汤羹', 2, 1, NOW(), NOW()),
(15, 4, 1, '主食', 3, 1, NOW(), NOW()),
(16, 4, 2, '家庭套餐', 1, 1, NOW(), NOW());

-- 张记面馆 菜品
INSERT INTO jiwei_merchant.dish (id, merchant_id, category_id, name, image, description, price, status, sales, create_time, update_time, create_user, update_user) VALUES
(1, 1, 1, '红烧牛肉面', 'https://api.dicebear.com/7.x/icons/svg?seed=beef', '大块牛腱肉，慢炖8小时，浓郁骨汤打底', 28.00, 1, 1523, NOW(), NOW(), 1, 1),
(2, 1, 1, '酸菜鱼片面', 'https://api.dicebear.com/7.x/icons/svg?seed=fish', '新鲜黑鱼片，老坛酸菜，酸辣开胃', 32.00, 1, 980, NOW(), NOW(), 1, 1),
(3, 1, 1, '番茄鸡蛋面', 'https://api.dicebear.com/7.x/icons/svg?seed=tomato', '浓郁番茄汤底，农家土鸡蛋', 18.00, 1, 2100, NOW(), NOW(), 1, 1),
(4, 1, 2, '宫保鸡丁饭', 'https://api.dicebear.com/7.x/icons/svg?seed=chicken', '鸡腿肉丁，花生碎，秘制宫保酱', 25.00, 1, 756, NOW(), NOW(), 1, 1),
(5, 1, 2, '鱼香肉丝饭', 'https://api.dicebear.com/7.x/icons/svg?seed=pork', '正宗鱼香口味，肉丝嫩滑', 22.00, 1, 634, NOW(), NOW(), 1, 1),
(6, 1, 3, '红油耳片', 'https://api.dicebear.com/7.x/icons/svg?seed=ear', '猪耳切薄片，红油蒜泥拌', 16.00, 1, 890, NOW(), NOW(), 1, 1),
(7, 1, 3, '凉拌黄瓜', 'https://api.dicebear.com/7.x/icons/svg?seed=cucumber', '拍黄瓜，蒜蓉醋汁', 8.00, 1, 1200, NOW(), NOW(), 1, 1);

-- 老王烧烤 菜品
INSERT INTO jiwei_merchant.dish (id, merchant_id, category_id, name, image, description, price, status, sales, create_time, update_time, create_user, update_user) VALUES
(8, 2, 5, '羊肉串（10串）', 'https://api.dicebear.com/7.x/icons/svg?seed=lamb', '呼伦贝尔草原羊肉，肥瘦相间', 38.00, 1, 3200, NOW(), NOW(), 2, 2),
(9, 2, 5, '牛肉串（10串）', 'https://api.dicebear.com/7.x/icons/svg?seed=beef1', '精选牛里脊，秘制腌料', 42.00, 1, 2800, NOW(), NOW(), 2, 2),
(10, 2, 5, '烤鸡翅（6只）', 'https://api.dicebear.com/7.x/icons/svg?seed=wing', '蜜汁奥尔良风味', 28.00, 1, 1900, NOW(), NOW(), 2, 2),
(11, 2, 6, '蒜蓉烤生蚝（6只）', 'https://api.dicebear.com/7.x/icons/svg?seed=oyster', '湛江大生蚝，蒜蓉粉丝蒸', 48.00, 1, 1560, NOW(), NOW(), 2, 2),
(12, 2, 6, '烤鱿鱼', 'https://api.dicebear.com/7.x/icons/svg?seed=squid', '整条大鱿鱼，铁板现烤', 35.00, 1, 890, NOW(), NOW(), 2, 2),
(13, 2, 7, '青岛啤酒（瓶）', 'https://api.dicebear.com/7.x/icons/svg?seed=beer', '冰镇青岛纯生，600ml', 12.00, 1, 4500, NOW(), NOW(), 2, 2),
(14, 2, 7, '王老吉', 'https://api.dicebear.com/7.x/icons/svg?seed=tea', '怕上火喝王老吉', 8.00, 1, 3200, NOW(), NOW(), 2, 2);

-- 蜀味川菜 菜品
INSERT INTO jiwei_merchant.dish (id, merchant_id, category_id, name, image, description, price, status, sales, create_time, update_time, create_user, update_user) VALUES
(15, 3, 9, '水煮牛肉', 'https://api.dicebear.com/7.x/icons/svg?seed=boiledbeef', '精选牛腱，花椒辣椒双重刺激', 58.00, 1, 890, NOW(), NOW(), 3, 3),
(16, 3, 9, '麻婆豆腐', 'https://api.dicebear.com/7.x/icons/svg?seed=tofu', '经典川味，麻辣鲜香嫩', 22.00, 1, 1560, NOW(), NOW(), 3, 3),
(17, 3, 9, '回锅肉', 'https://api.dicebear.com/7.x/icons/svg?seed=pork2', '二刀肉配蒜苗，郫县豆瓣', 38.00, 1, 1200, NOW(), NOW(), 3, 3),
(18, 3, 10, '香辣干锅虾', 'https://api.dicebear.com/7.x/icons/svg?seed=shrimp', '大虾配藕片土豆，干锅慢煸', 68.00, 1, 670, NOW(), NOW(), 3, 3),
(19, 3, 10, '干锅花菜', 'https://api.dicebear.com/7.x/icons/svg?seed=cauliflower', '有机花菜，五花肉片提香', 32.00, 1, 540, NOW(), NOW(), 3, 3),
(20, 3, 11, '蛋炒饭', 'https://api.dicebear.com/7.x/icons/svg?seed=rice', '粒粒分明，锅气十足', 15.00, 1, 2300, NOW(), NOW(), 3, 3);

-- 外婆家 菜品
INSERT INTO jiwei_merchant.dish (id, merchant_id, category_id, name, image, description, price, status, sales, create_time, update_time, create_user, update_user) VALUES
(21, 4, 13, '红烧肉', 'https://api.dicebear.com/7.x/icons/svg?seed=redpork', '五花肉慢炖2小时，肥而不腻', 48.00, 1, 980, NOW(), NOW(), 4, 4),
(22, 4, 13, '糖醋排骨', 'https://api.dicebear.com/7.x/icons/svg?seed=ribs', '镇江香醋配冰糖，酸甜适中', 45.00, 1, 780, NOW(), NOW(), 4, 4),
(23, 4, 13, '清炒时蔬', 'https://api.dicebear.com/7.x/icons/svg?seed=veggie', '当季新鲜蔬菜，蒜蓉清炒', 18.00, 1, 1500, NOW(), NOW(), 4, 4),
(24, 4, 14, '番茄蛋汤', 'https://api.dicebear.com/7.x/icons/svg?seed=soup', '浓郁番茄，农家土鸡蛋', 12.00, 1, 890, NOW(), NOW(), 4, 4),
(25, 4, 15, '白米饭', 'https://api.dicebear.com/7.x/icons/svg?seed=rice2', '五常大米，颗粒饱满', 3.00, 1, 5000, NOW(), NOW(), 4, 4);

-- 口味
INSERT INTO jiwei_merchant.dish_flavor (dish_id, name, value) VALUES
(1, '辣度', '["不辣","微辣","中辣","特辣"]'),
(1, '面条', '["细面","宽面","刀削面"]'),
(2, '辣度', '["微辣","中辣","特辣"]'),
(8, '辣度', '["不辣","微辣","中辣","重辣"]'),
(15, '辣度', '["中辣","特辣","变态辣"]'),
(16, '辣度', '["微辣","中辣","特辣"]'),
(21, '份量', '["小份","中份","大份"]');

-- 套餐
INSERT INTO jiwei_merchant.setmeal (id, merchant_id, category_id, name, image, description, price, status, create_time, update_time) VALUES
(1, 1, 4, '一人食满足套餐', 'https://api.dicebear.com/7.x/icons/svg?seed=set1', '红烧牛肉面 + 凉拌黄瓜 + 饮料', 38.00, 1, NOW(), NOW()),
(2, 1, 4, '双人温馨套餐', 'https://api.dicebear.com/7.x/icons/svg?seed=set2', '酸菜鱼片面 + 宫保鸡丁饭 + 红油耳片 + 2杯饮料', 72.00, 1, NOW(), NOW()),
(3, 2, 8, '深夜畅饮套餐', 'https://api.dicebear.com/7.x/icons/svg?seed=set3', '羊肉串x2 + 鸡翅 + 啤酒x4', 118.00, 1, NOW(), NOW()),
(4, 3, 12, '四人聚会套餐', 'https://api.dicebear.com/7.x/icons/svg?seed=set4', '水煮牛肉+麻婆豆腐+回锅肉+干锅虾+蛋炒饭x4', 198.00, 1, NOW(), NOW()),
(5, 4, 16, '三口之家套餐', 'https://api.dicebear.com/7.x/icons/svg?seed=set5', '红烧肉+糖醋排骨+清炒时蔬+番茄蛋汤+米饭x3', 108.00, 1, NOW(), NOW());

INSERT INTO jiwei_merchant.setmeal_dish (setmeal_id, dish_id, name, price, copies) VALUES
(1, 1, '红烧牛肉面', 28.00, 1), (1, 7, '凉拌黄瓜', 8.00, 1),
(2, 2, '酸菜鱼片面', 32.00, 1), (2, 4, '宫保鸡丁饭', 25.00, 1), (2, 6, '红油耳片', 16.00, 1),
(3, 8, '羊肉串（10串）', 38.00, 2), (3, 10, '烤鸡翅（6只）', 28.00, 1), (3, 13, '青岛啤酒', 12.00, 4),
(4, 15, '水煮牛肉', 58.00, 1), (4, 16, '麻婆豆腐', 22.00, 1), (4, 17, '回锅肉', 38.00, 1), (4, 18, '香辣干锅虾', 68.00, 1),
(5, 21, '红烧肉', 48.00, 1), (5, 22, '糖醋排骨', 45.00, 1), (5, 23, '清炒时蔬', 18.00, 1), (5, 24, '番茄蛋汤', 12.00, 1);

-- ==========================================
-- jiwei_trade: 订单
-- ==========================================
INSERT INTO jiwei_trade.orders (id, number, user_id, merchant_id, address_book_id, address_detail, amount, discount_amount, delivery_fee, pay_amount, pay_method, pay_status, status, transaction_id, remark, is_seckill, pay_time, create_time, update_time) VALUES
(1, 'SV20260801001', 1, 1, 1, '浙江省杭州市上城区延安路258号湖滨银泰A座1206', 66.00, 6.00, 3.00, 63.00, 1, 1, 5, 'wx_txn_mock_001', '不要香菜', 0, '2026-08-01 12:15:00', '2026-08-01 12:00:00', '2026-08-01 13:30:00'),
(2, 'SV20260802001', 1, 2, 1, '浙江省杭州市上城区延安路258号湖滨银泰A座1206', 146.00, 20.00, 5.00, 131.00, 1, 1, 5, 'wx_txn_mock_002', '多点辣椒', 0, '2026-08-02 20:30:00', '2026-08-02 20:15:00', '2026-08-02 21:45:00'),
(3, 'SV20260803001', 2, 3, 3, '浙江省杭州市滨江区江南大道228号星光大道3幢1502', 58.00, 0, 3.00, 61.00, 1, 1, 3, 'wx_txn_mock_003', '', 0, '2026-08-03 11:00:00', '2026-08-03 10:45:00', NOW()),
(4, 'SV20260804001', 3, 2, 4, '浙江省杭州市下城区武林路168号', 154.00, 12.00, 5.00, 147.00, 1, 1, 5, 'wx_txn_mock_004', '多放孜然', 0, '2026-08-04 22:00:00', '2026-08-04 21:40:00', '2026-08-05 00:30:00'),
(5, 'SV20260804002', 4, 4, 5, '浙江省杭州市余杭区文一西路998号海创园5号楼', 51.00, 5.00, 3.00, 49.00, 1, 1, 5, 'wx_txn_mock_005', '', 0, '2026-08-04 12:00:00', '2026-08-04 11:50:00', '2026-08-04 13:00:00'),
(6, 'SV20260805001', 5, 3, 3, '浙江省杭州市滨江区江南大道228号', 126.00, 0, 3.00, 129.00, 1, 0, 1, NULL, '少油', 0, NULL, '2026-08-05 08:30:00', NOW()),
(7, 'SV20260805002', 1, 1, 2, '浙江省杭州市西湖区文三路478号华星时代广场', 32.00, 0, 3.00, 35.00, 1, 0, 2, NULL, '', 0, NULL, '2026-08-05 09:00:00', NOW());

INSERT INTO jiwei_trade.order_detail (order_id, name, image, dish_flavor, amount, number) VALUES
(1, '红烧牛肉面', 'https://api.dicebear.com/7.x/icons/svg?seed=beef', '中辣,细面', 28.00, 1),
(1, '凉拌黄瓜', 'https://api.dicebear.com/7.x/icons/svg?seed=cucumber', '', 8.00, 1),
(1, '番茄鸡蛋面', 'https://api.dicebear.com/7.x/icons/svg?seed=tomato', '', 18.00, 1),
(2, '羊肉串（10串）', 'https://api.dicebear.com/7.x/icons/svg?seed=lamb', '重辣', 38.00, 2),
(2, '烤鸡翅（6只）', 'https://api.dicebear.com/7.x/icons/svg?seed=wing', '', 28.00, 1),
(2, '青岛啤酒（瓶）', 'https://api.dicebear.com/7.x/icons/svg?seed=beer', '', 12.00, 2),
(3, '水煮牛肉', 'https://api.dicebear.com/7.x/icons/svg?seed=boiledbeef', '特辣', 58.00, 1),
(4, '羊肉串（10串）', 'https://api.dicebear.com/7.x/icons/svg?seed=lamb', '中辣', 38.00, 2),
(4, '蒜蓉烤生蚝（6只）', 'https://api.dicebear.com/7.x/icons/svg?seed=oyster', '', 48.00, 1),
(4, '烤鱿鱼', 'https://api.dicebear.com/7.x/icons/svg?seed=squid', '', 35.00, 1),
(5, '糖醋排骨', 'https://api.dicebear.com/7.x/icons/svg?seed=ribs', '', 45.00, 1),
(5, '白米饭', 'https://api.dicebear.com/7.x/icons/svg?seed=rice2', '', 3.00, 2),
(6, '香辣干锅虾', 'https://api.dicebear.com/7.x/icons/svg?seed=shrimp', '', 68.00, 1),
(6, '麻婆豆腐', 'https://api.dicebear.com/7.x/icons/svg?seed=tofu', '', 22.00, 1),
(6, '蛋炒饭', 'https://api.dicebear.com/7.x/icons/svg?seed=rice', '', 15.00, 2),
(7, '酸菜鱼片面', 'https://api.dicebear.com/7.x/icons/svg?seed=fish', '中辣', 32.00, 1);

-- ==========================================
-- jiwei_market: 秒杀 + 优惠券
-- ==========================================
INSERT INTO jiwei_market.seckill_activity (id, name, dish_id, seckill_price, stock, limit_per_user, start_time, end_time, status, create_time) VALUES
(1, '午市秒杀-红烧牛肉面', 1, 15.80, 20, 1, '2026-08-05 11:00:00', '2026-08-05 13:00:00', 1, NOW()),
(2, '晚市秒杀-羊肉串', 8, 19.90, 15, 2, '2026-08-05 18:00:00', '2026-08-05 21:00:00', 0, NOW()),
(3, '周末秒杀-糖醋排骨', 22, 25.00, 10, 1, '2026-08-08 10:00:00', '2026-08-08 20:00:00', 0, NOW());

INSERT INTO jiwei_market.coupon_template (id, name, type, threshold, discount_value, total_count, per_user_limit, valid_days, status, create_time) VALUES
(1, '新人专享-满30减8', 1, 30.00, 8.00, 200, 1, 7, 1, NOW()),
(2, '夏日畅饮-满50减12', 1, 50.00, 12.00, 100, 2, 14, 1, NOW()),
(3, '全场8折券', 2, 0, 0.80, 50, 1, 3, 1, NOW()),
(4, '无门槛5元券', 3, 0, 5.00, 500, 3, 30, 1, NOW());

INSERT INTO jiwei_market.user_coupon (template_id, user_id, status, receive_time, expire_time) VALUES
(1, 1, 0, '2026-08-01 10:00:00', '2026-08-08 10:00:00'),
(2, 1, 1, '2026-08-02 14:00:00', '2026-08-16 14:00:00'),
(4, 2, 0, '2026-08-03 09:00:00', '2026-09-02 09:00:00'),
(3, 3, 0, '2026-08-04 16:00:00', '2026-08-07 16:00:00'),
(1, 5, 0, '2026-08-05 08:00:00', '2026-08-12 08:00:00');

-- ==========================================
-- jiwei_social: 评价 + 笔记 + 评论 + 关注
-- ==========================================
INSERT INTO jiwei_social.review (id, user_id, order_id, dish_id, rating, content, tags, is_ai_assisted, audit_status, like_count, create_time) VALUES
(1, 1, 1, 1, 5, '面条劲道，牛肉大块！汤底很浓郁，推荐微辣口味', '["分量足","味道好"]', 0, 1, 23, '2026-08-01 14:00:00'),
(2, 1, 2, 8, 5, '深夜放毒！老王家的羊肉串绝了，每次加班完必来', '["深夜食堂","必吃榜"]', 0, 1, 45, '2026-08-02 22:00:00'),
(3, 2, 3, 15, 4, '水煮牛肉非常嫩，就是太辣了哈哈，下次点中辣', '["够味","偏辣"]', 0, 1, 12, '2026-08-03 12:00:00'),
(4, 3, 4, 11, 5, '生蚝个头超级大！蒜蓉粉丝的搭配无敌，一口气吃了两打', '["海鲜","超值"]', 0, 1, 38, '2026-08-04 23:00:00'),
(5, 4, 5, 22, 5, '糖醋排骨酸甜刚好，肉很嫩，比我自己做的好吃太多了', '["家常味","推荐"]', 0, 1, 15, '2026-08-04 13:30:00');

INSERT INTO jiwei_social.note (id, user_id, title, content, merchant_id, topic_tags, location, like_count, comment_count, collect_count, view_count, audit_status, is_top, create_time, update_time) VALUES
(1, 1, '杭州必吃！张记面馆红烧牛肉面深度测评', '作为一个面食爱好者，今天终于来打卡传说中的张记面馆。牛肉分量真的惊到我了，起码有七八块大块牛腱肉！炖得超级软烂。汤底肉眼可见的浓郁，能喝到骨头的鲜味。重点推荐他们家的细面，很吸汤，每一根面条都裹满了牛肉汤的味道。强烈建议加一份红油耳片，脆爽开胃！', 1, '["美食探店","面食地图","杭州吃喝"]', '张记面馆·杭州上城', 328, 3, 156, 2800, 1, 0, '2026-08-01 15:30:00', NOW()),
(2, 3, '杭州夜宵天花板！老王烧烤全攻略', '凌晨一点还在排队的老王烧烤，到底值不值？答案是：太值了！羊肉串必须点！用的是呼伦贝尔的羊肉，完全没有膻味。一定要趁热吃，肥肉部分烤得焦焦的，咬下去那个声音太上头了。蒜蓉生蚝也是招牌，每个蚝肉都有手掌那么大。建议点个畅饮套餐，啤酒配串，这才是夏天该有的样子！', 2, '["深夜食堂","烧烤","杭州夜宵榜"]', '老王烧烤·杭州拱墅', 567, 2, 234, 4500, 1, 1, '2026-08-03 01:20:00', NOW()),
(3, 2, '蜀味川菜四人聚会全记录，附点单攻略', '周末和姐妹们聚会在蜀味川菜，四个人点了聚会套餐，真的太划算了！水煮牛肉是全场MVP，牛肉片又薄又嫩，一勺热油浇上去那个滋滋声简直了。麻婆豆腐很正宗，用的是嫩豆腐，麻辣够味，拌饭一绝！干锅虾超级大只，配菜的藕片土豆比虾还好吃。四个人吃了198的套餐，人均不到50，这个性价比在杭州真的很能打！', 3, '["川菜","聚餐","性价比"]', '蜀味川菜·杭州西湖', 256, 2, 98, 2100, 1, 0, '2026-08-04 14:00:00', NOW()),
(4, 5, '减脂期也能吃！外婆家私房菜健康点单秘籍', '作为健身党，外食最怕的就是油腻。但是外婆家私房菜真的可以！红烧肉我只点了小份解馋。糖醋排骨用的是瘦肉排，酸甜刚好不会腻。清炒时蔬是我每次必点的，当季的菜心很嫩，蒜蓉清炒很清爽。番茄蛋汤很浓郁，应该加了不少番茄熬的。吃下来完全没有负罪感，强烈推荐给减脂期的姐妹们！', 4, '["轻食","减脂餐","健康"]', '外婆家私房菜·杭州滨江', 189, 2, 67, 1600, 1, 0, '2026-08-05 10:00:00', NOW());

INSERT INTO jiwei_social.note_like (note_id, user_id, create_time) VALUES
(1,2,NOW()),(1,3,NOW()),(1,4,NOW()),(1,5,NOW()),
(2,1,NOW()),(2,2,NOW()),(2,4,NOW()),(2,5,NOW()),
(3,1,NOW()),(3,3,NOW()),(3,4,NOW()),
(4,1,NOW()),(4,2,NOW()),(4,3,NOW());

INSERT INTO jiwei_social.follow (follower_id, followee_id, create_time) VALUES
(1,2,NOW()),(1,3,NOW()),(2,1,NOW()),(2,5,NOW()),
(3,1,NOW()),(3,2,NOW()),(4,1,NOW()),(4,3,NOW()),
(5,1,NOW()),(5,2,NOW()),(5,4,NOW());

INSERT INTO jiwei_social.comment (id, note_id, user_id, parent_id, reply_to_user_id, content, like_count, create_time) VALUES
(1,1,2,NULL,NULL,'我也超爱张记！每周必去一次', 12, '2026-08-01 16:00:00'),
(2,1,3,NULL,NULL,'细面真的比宽面好吃！', 8, '2026-08-01 17:30:00'),
(3,1,1,2,3,'下次试试宽面哈哈，吸汤能力更强', 3, '2026-08-01 18:00:00'),
(4,2,1,NULL,NULL,'老王就是杭州夜宵的尽头', 28, '2026-08-03 09:00:00'),
(5,2,4,NULL,NULL,'凌晨一点排队也太拼了吧', 15, '2026-08-03 10:30:00'),
(6,2,3,5,4,'真的值得！你去一次就知道了', 6, '2026-08-03 11:00:00'),
(7,3,1,NULL,NULL,'这个套餐确实划算，下周约起来', 10, '2026-08-04 15:00:00'),
(8,4,2,NULL,NULL,'减脂期看到红烧肉好痛苦哈哈哈', 20, '2026-08-05 11:00:00'),
(9,4,5,8,2,'小份解馋就好啦！可以多点蔬菜', 8, '2026-08-05 11:30:00');
