-- ==========================================
-- 12_fix_seckill_time.sql: 把秒杀活动时间重置为"当前进行中"
-- 演示数据活动时间随初始化日期过期，导致无法抢购。
-- 本脚本用 NOW() 相对时间刷新，可重复执行（每次执行都会把活动拉回进行中）。
-- ==========================================

-- 午市秒杀-红烧牛肉面（进行中，还有 22 小时）
UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = DATE_SUB(NOW(), INTERVAL 2 HOUR),
    end_time = DATE_ADD(NOW(), INTERVAL 22 HOUR)
WHERE id = 1;

-- 晚市秒杀-羊肉串（进行中，还有 2 小时）
UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR),
    end_time = DATE_ADD(NOW(), INTERVAL 2 HOUR)
WHERE id = 2;

-- 周末秒杀-糖醋排骨（进行中，还有 3 小时）
UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = NOW(),
    end_time = DATE_ADD(NOW(), INTERVAL 3 HOUR)
WHERE id = 3;

-- 红烧牛肉面限时秒杀（进行中，还有 5 小时）
UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = DATE_SUB(NOW(), INTERVAL 30 MINUTE),
    end_time = DATE_ADD(NOW(), INTERVAL 5 HOUR)
WHERE id = 4;

-- 双十一活动提前到今天（进行中）
UPDATE jiwei_market.seckill_activity SET
    status = 1,
    start_time = DATE_SUB(NOW(), INTERVAL 1 HOUR),
    end_time = DATE_ADD(NOW(), INTERVAL 2 DAY)
WHERE id = 5;
