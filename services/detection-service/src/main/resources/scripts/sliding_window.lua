-- Atomic sliding-window update (one round trip, no race between add / trim / read).
-- KEYS[1] = window key
-- ARGV[1] = score (event time, epoch millis)   ARGV[2] = member
-- ARGV[3] = window length millis               ARGV[4] = ttl millis
-- Returns members (with scores) inside [score - window, score]
local score  = tonumber(ARGV[1])
local window = tonumber(ARGV[3])
redis.call('ZADD', KEYS[1], score, ARGV[2])
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. (score - window))
redis.call('PEXPIRE', KEYS[1], ARGV[4])
return redis.call('ZRANGEBYSCORE', KEYS[1], score - window, score, 'WITHSCORES')
