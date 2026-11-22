-- Atomic sliding-window update (one round trip, no race between add / trim / read).
-- KEYS[1] = window key
-- ARGV[1] = score (event time, epoch millis)   ARGV[2] = member
-- ARGV[3] = window length millis               ARGV[4] = ttl millis
-- ARGV[5] = retention millis (how far behind the NEWEST entry we keep data, >= window)
-- Returns members (with scores) inside [score - window, score + window].
--
-- Order-independent (ADR-005 §11): transactions of one account can be evaluated out of time
-- order, so the caller must see entries on BOTH sides of the new one, and trimming is relative
-- to the newest entry (not the current one) so a late, earlier-timestamped arrival still finds
-- what it needs. The current entry's own look-back is never trimmed.
local score     = tonumber(ARGV[1])
local window    = tonumber(ARGV[3])
local retention = tonumber(ARGV[5])
redis.call('ZADD', KEYS[1], score, ARGV[2])
local newest = tonumber(redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')[2])
local cutoff = math.min(newest - retention, score - window)
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', '(' .. cutoff)
redis.call('PEXPIRE', KEYS[1], ARGV[4])
return redis.call('ZRANGEBYSCORE', KEYS[1], score - window, score + window, 'WITHSCORES')
