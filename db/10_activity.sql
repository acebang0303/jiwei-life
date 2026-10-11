-- ==========================================
-- 10_activity.sql: 活动(banner) + 用户行为记录 + 补充种子数据
-- 依赖 01-06 + 99_seed_data 已执行
-- ==========================================

-- 活动表（首页轮播 banner 对应活动）
CREATE TABLE IF NOT EXISTS jiwei_market.activity (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL COMMENT '活动标题',
    subtitle VARCHAR(200) COMMENT '活动副标题',
    bg_color VARCHAR(100) COMMENT 'banner 背景渐变',
    type TINYINT NOT NULL COMMENT '1=跳转店铺 2=跳转秒杀 3=跳转优惠券 4=跳转笔记详情',
    target_id BIGINT COMMENT '跳转目标ID',
    sort INT DEFAULT 0 COMMENT '排序',
    status TINYINT DEFAULT 1 COMMENT '1=上架 0=下架',
    create_time DATETIME,
    update_time DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='首页活动';

-- 用户行为记录（用于 AI 个性化推荐）
CREATE TABLE IF NOT EXISTS jiwei_user.user_behavior (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    type VARCHAR(30) NOT NULL COMMENT 'LIKE_NOTE/COLLECT_NOTE/COMMENT_NOTE/VIEW_MERCHANT',
    target_id BIGINT NOT NULL COMMENT '目标ID(笔记ID或店铺ID)',
    create_time DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='用户行为记录';
-- ==========================================
-- 秒杀活动时间修正为"进行中"（种子数据时间已过期）
-- ==========================================
UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = DATE_SUB(NOW(), INTERVAL 2 HOUR),
    end_time = DATE_ADD(NOW(), INTERVAL 22 HOUR)
WHERE id = 1;

UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR),
    end_time = DATE_ADD(NOW(), INTERVAL 2 HOUR)
WHERE id = 2;

UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = NOW(),
    end_time = DATE_ADD(NOW(), INTERVAL 3 HOUR)
WHERE id = 3;

-- 新增一场进行中的秒杀（蜀味川菜·水煮牛肉）
INSERT IGNORE INTO jiwei_market.seckill_activity
(id, name, dish_id, seckill_price, stock, limit_per_user, start_time, end_time, status, create_time) VALUES
(4, '整点秒杀-水煮牛肉', 15, 29.90, 30, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 5 HOUR), 1, NOW());

-- ==========================================
-- 活动种子数据（首页轮播 4 个活动）
-- ==========================================
INSERT IGNORE INTO jiwei_market.activity
(id, title, subtitle, bg_color, type, target_id, sort, status, create_time, update_time) VALUES
(1, '张记面馆·招牌面免费升级', '进店领「满30减8」新人券，加量不加价', 'linear-gradient(135deg, #FF7A3D, #F06A2E)', 1, 1, 1, 1, NOW(), NOW()),
(2, '午市秒杀 15.8 元起', '红烧牛肉面秒杀进行中，手慢无！', 'linear-gradient(135deg, #FFB98A, #FF7A3D)', 2, 1, 2, 1, NOW(), NOW()),
(3, '新人专享大礼包', '首单立减 ¥15，全场 8 折券等你领', 'linear-gradient(135deg, #E8A13C, #FF9A5A)', 3, 3, 3, 1, NOW(), NOW()),
(4, '吃货笔记精选', '看看大家都爱点什么，抄作业不踩雷', 'linear-gradient(135deg, #5B8DB8, #4C9A6A)', 4, 1, 4, 1, NOW(), NOW());

-- ==========================================
-- 用户行为种子数据（供 AI 推荐引擎构建画像）
-- ==========================================
INSERT IGNORE INTO jiwei_user.user_behavior (user_id, type, target_id, create_time) VALUES
-- 用户1(吃货小陈): 喜欢川菜/烧烤，点赞收藏了蜀味、烧烤店铺的笔记，浏览过店铺
(1, 'LIKE_NOTE', 1, NOW()), (1, 'COLLECT_NOTE', 1, NOW()),
(1, 'VIEW_MERCHANT', 3, NOW()), (1, 'VIEW_MERCHANT', 2, NOW()), (1, 'VIEW_MERCHANT', 1, NOW()),
(1, 'LIKE_NOTE', 3, NOW()), (1, 'COLLECT_NOTE', 4, NOW()),
-- 用户2(甜品控小美): 喜欢面馆和家常菜
(2, 'VIEW_MERCHANT', 1, NOW()), (2, 'VIEW_MERCHANT', 4, NOW()),
(2, 'LIKE_NOTE', 2, NOW()), (2, 'COLLECT_NOTE', 2, NOW()),
-- 用户3(老饕李哥): 烧烤狂热粉
(3, 'VIEW_MERCHANT', 2, NOW()), (3, 'VIEW_MERCHANT', 3, NOW()),
(3, 'LIKE_NOTE', 4, NOW()), (3, 'COLLECT_NOTE', 3, NOW()),
-- 用户4(加班小王): 面馆快餐
(4, 'VIEW_MERCHANT', 1, NOW()), (4, 'LIKE_NOTE', 2, NOW()),
-- 用户5(健身达人Amy): 轻食家常菜
(5, 'VIEW_MERCHANT', 4, NOW()), (5, 'COLLECT_NOTE', 2, NOW());

-- ==========================================
-- 补充笔记（让 Feed/热门更丰富，全部关联真实店铺与用户）
-- ==========================================
INSERT IGNORE INTO jiwei_social.note
(id, user_id, title, content, images, merchant_id, topic_tags, location, like_count, comment_count, collect_count, view_count, audit_status, is_top, create_time, update_time) VALUES
(330, 2, '张记面馆的红烧牛肉面太顶了！', '一碗面治愈加班夜。牛肉炖得酥烂，汤头浓郁，面条筋道。加一份红油耳片，绝配！价格也实在，人均30吃到撑。', '["https://api.dicebear.com/7.x/icons/svg?seed=beef","https://api.dicebear.com/7.x/icons/svg?seed=ear"]', 1, '["面馆","杭城美食"]', '杭州·上城区', 68, 9, 22, 890, 1, 0, NOW(), NOW()),
(331, 3, '深夜烧烤局：老王烧烤实测', '晚上十点来依旧满座，羊肉串肥瘦相间入口爆汁，生蚝个头大又新鲜，配冰镇啤酒绝了。人均80，值得再来。', '["https://api.dicebear.com/7.x/icons/svg?seed=lamb","https://api.dicebear.com/7.x/icons/svg?seed=oyster","https://api.dicebear.com/7.x/icons/svg?seed=beer"]', 2, '["烧烤","深夜食堂"]', '杭州·拱墅区', 156, 23, 45, 2100, 1, 1, NOW(), NOW()),
(332, 1, '蜀味川菜：水煮牛肉的正确打开方式', '成都师傅掌勺果然地道！水煮牛肉麻辣过瘾，牛肉片嫩滑，配菜豆芽脆爽。麻婆豆腐拌饭能炫两碗，聚会首选。', '["https://api.dicebear.com/7.x/icons/svg?seed=boiledbeef","https://api.dicebear.com/7.x/icons/svg?seed=tofu"]', 3, '["川菜","聚餐"]', '杭州·西湖区', 42, 8, 15, 760, 1, 0, NOW(), NOW()),
(333, 5, '外婆家私房菜，家常菜天花板', '红烧肉肥而不腻入口即化，糖醋排骨酸甜刚好。家庭聚餐点套餐最划算，人均40。环境干净，服务热情。', '["https://api.dicebear.com/7.x/icons/svg?seed=redpork","https://api.dicebear.com/7.x/icons/svg?seed=ribs"]', 4, '["家常菜","家庭聚餐"]', '杭州·滨江区', 33, 6, 12, 540, 1, 0, NOW(), NOW()),
(334, 4, '打工人工作餐：张记盖浇饭', '宫保鸡丁饭和鱼香肉丝饭换着吃，出餐快，分量足，午休时间刚好。番茄鸡蛋面也推荐，汤鲜面滑。', '["https://api.dicebear.com/7.x/icons/svg?seed=chicken","https://api.dicebear.com/7.x/icons/svg?seed=tomato"]', 1, '["工作餐","面食"]', '杭州·上城区', 21, 4, 8, 320, 1, 0, NOW(), NOW());

-- 同步补充行为：热门笔记的点赞记录（让 like_count 与 note_like 一致）
INSERT IGNORE INTO jiwei_social.note_like (note_id, user_id, create_time) VALUES
(330, 1, NOW()), (330, 3, NOW()), (331, 1, NOW()), (331, 4, NOW()), (331, 5, NOW()),
(332, 2, NOW()), (332, 3, NOW()), (333, 2, NOW()), (333, 1, NOW()), (334, 3, NOW());

-- 补充关注关系：让新用户点关注有真实反馈
INSERT IGNORE INTO jiwei_social.follow (follower_id, followee_id, create_time) VALUES
(1, 2, NOW()), (1, 5, NOW()), (2, 1, NOW()), (3, 1, NOW()), (3, 2, NOW());
