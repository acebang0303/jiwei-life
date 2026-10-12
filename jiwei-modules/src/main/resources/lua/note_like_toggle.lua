-- 非热点笔记的「原子点赞切换」
--
-- 背景：原实现是 Java 侧 read-then-write（先 SISMEMBER/HGET 判断，再 SADD/SREM + HINCRBY），
-- 同一用户并发双击时两条请求都会读到「未点赞」，于是各加一次计数 -> 多记。
-- 把判断与写入整体放进 Lua，Redis 单线程执行，天然原子。
--
-- KEYS[1] note:like:count        HASH  field=noteId value=点赞数
-- KEYS[2] note:like:users:{id}   SET   已点赞用户集合
-- KEYS[3] note:like:events       LIST  点赞事件队列（供消费端攒批落库）
-- ARGV[1] noteId
-- ARGV[2] userId
-- ARGV[3] 事件时间戳(ms)
-- 返回 {likedFlag(1=点赞 0=取消), 切换后计数}
local noteId = ARGV[1]
local userId = ARGV[2]
local ts = ARGV[3]

local alreadyLiked = redis.call('SISMEMBER', KEYS[2], userId)
local liked
if alreadyLiked == 1 then
    redis.call('SREM', KEYS[2], userId)
    liked = 0
else
    redis.call('SADD', KEYS[2], userId)
    liked = 1
end

local delta = (liked == 1) and 1 or -1
local count = redis.call('HINCRBY', KEYS[1], noteId, delta)

-- 事件负载在这里拼，保证与 Java 侧 JSON 字段名一致（消费端用 fastjson 反序列化）
local eventType = (liked == 1) and 'LIKE' or 'UNLIKE'
redis.call('RPUSH', KEYS[3],
    '{"type":"' .. eventType .. '","noteId":' .. noteId ..
    ',"userId":' .. userId .. ',"ts":' .. ts .. '}')

-- 返回 "<likedFlag>,<切换后计数>"（用字符串返回，避免 multi-bulk 整数在 Java 侧的类型转换歧义）
return liked .. ',' .. count
