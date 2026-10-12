-- 热点笔记本地缓冲的「原子批量刷写」
--
-- 背景：原实现是一次 pipeline 里逐条 SADD/SREM + 按「意图条数」HINCRBY。
-- 两个问题：
--   1. 多实例部署时，两个实例都可能对同一 (note,user) 发 SADD（一个进一个不进），
--      但都按意图 +1 -> 计数被重复累加；
--   2. 冷热切换窗口内，同一用户可能同时走 Redis 直连与本地缓冲两条路径。
-- 改为在 Lua 里按 SADD/SREM 的「实际影响结果」累加 delta：已经存在的用户再加不会再计一次，
-- 重放、多实例、跨路径都能幂等收敛。
--
-- KEYS[1] note:like:count        HASH
-- KEYS[2] note:like:users:{id}   SET
-- KEYS[3] note:like:events       LIST
-- ARGV[1] noteId
-- ARGV[2] likeCount L
-- ARGV[3..2+L] like userIds
-- ARGV[3+L] unlikeCount U
-- ARGV[4+L..3+L+U] unlike userIds
-- ARGV[4+L+U] 事件时间戳(ms)
-- 返回 {实际净增量, 实际推入事件条数}
local noteId = ARGV[1]
local likeCount = tonumber(ARGV[2])
local unlikeCount = tonumber(ARGV[3 + likeCount])
local ts = ARGV[4 + likeCount + unlikeCount]

local delta = 0
local pushed = 0

for i = 1, likeCount do
    local uid = ARGV[2 + i]
    if redis.call('SADD', KEYS[2], uid) == 1 then
        delta = delta + 1
        pushed = pushed + 1
        redis.call('RPUSH', KEYS[3],
            '{"type":"LIKE","noteId":' .. noteId .. ',"userId":' .. uid .. ',"ts":' .. ts .. '}')
    end
end

for i = 1, unlikeCount do
    local uid = ARGV[3 + likeCount + i]
    if redis.call('SREM', KEYS[2], uid) == 1 then
        delta = delta - 1
        pushed = pushed + 1
        redis.call('RPUSH', KEYS[3],
            '{"type":"UNLIKE","noteId":' .. noteId .. ',"userId":' .. uid .. ',"ts":' .. ts .. '}')
    end
end

if delta ~= 0 then
    redis.call('HINCRBY', KEYS[1], noteId, delta)
end

-- 返回 "<实际净增量>,<实际推入事件条数>"（字符串返回，避免 multi-bulk 整数类型转换歧义）
return delta .. ',' .. pushed
