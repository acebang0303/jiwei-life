-- ==========================================
-- 吉味生活 · JiweiLife
-- AI 服务 NL2SQL 专用只读账号
-- ==========================================
-- 用途：jiwei-ai 的 bizDataSource 用它连业务库跑大模型生成的 SQL。
-- 权限只给 SELECT，即使 SQL 文本校验被绕过，服务端也会拒绝写操作。
--
-- 执行方式（密码请自行替换，并同步写入项目根 .env）：
--   docker exec -i jiwei-mysql mysql -uroot -proot123 < db/08_ai_readonly_user.sql
--
-- 对应配置：
--   biz-db:
--     read-only-username: jiwei_ai_ro
--     read-only-password: ${JIWEI_MYSQL_RO_PASSWORD}

CREATE USER IF NOT EXISTS 'jiwei_ai_ro'@'%' IDENTIFIED BY 'jiwei_ro123';

-- 先清空历史权限，保证脚本可重复执行
REVOKE ALL PRIVILEGES, GRANT OPTION FROM 'jiwei_ai_ro'@'%';

-- 只读：仅 SELECT 与 SHOW VIEW，不含任何写 / DDL 权限
GRANT SELECT, SHOW VIEW ON jiwei_merchant.* TO 'jiwei_ai_ro'@'%';
GRANT SELECT, SHOW VIEW ON jiwei_trade.* TO 'jiwei_ai_ro'@'%';
GRANT SELECT, SHOW VIEW ON jiwei_social.* TO 'jiwei_ai_ro'@'%';
GRANT SELECT, SHOW VIEW ON jiwei_user.* TO 'jiwei_ai_ro'@'%';

FLUSH PRIVILEGES;

-- 验证：应只看到 SELECT / SHOW VIEW
-- SHOW GRANTS FOR 'jiwei_ai_ro'@'%';
