-- ==========================================
-- 吉味生活 · JiweiLife
-- 一键初始化所有数据库
-- 用法: mysql -u root -p < init_all.sql
-- ==========================================

SOURCE 01_auth.sql;
SOURCE 02_user.sql;
SOURCE 03_merchant.sql;
SOURCE 04_trade.sql;
SOURCE 05_market.sql;
SOURCE 06_social.sql;

-- 示例种子数据（默认账号 admin / merchant01 / merchant02...，密码均 123456）
-- 如需更大规模演示数据可另行执行 98_mock_data.sql
SOURCE 99_seed_data.sql;

-- 活动(banner) + 用户行为 + 补充种子数据
SOURCE 10_activity.sql;

-- pgvector 请在 PostgreSQL 中单独执行:
-- psql -U postgres -d jiwei_ai -f 07_pgvector.sql
