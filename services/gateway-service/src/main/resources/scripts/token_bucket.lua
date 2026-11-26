-- Token bucket, atomic in Redis (Phase 7 rate limiting).
-- KEYS[1] = bucket key (one per caller)
-- ARGV[1] = capacity (max burst), ARGV[2] = refill rate in tokens per second
-- Returns { allowed (1|0), tokens left (floor), ms until the next token when denied }
--
-- Uses Redis' own clock (TIME) so every gateway pod agrees on "now", whatever their system clocks say.

local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])
if tokens == nil then
  tokens = capacity
  ts = now
end

-- Refill for the time since the last request, never above capacity
tokens = math.min(capacity, tokens + math.max(0, now - ts) * rate / 1000)

local allowed = 0
local retry_ms = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retry_ms = math.ceil((1 - tokens) * 1000 / rate)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', now)
-- A bucket idle long enough to be full again carries no information: let it expire
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * 1000 / rate) + 1000)

return { allowed, math.floor(tokens), retry_ms }
