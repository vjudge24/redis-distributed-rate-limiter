package com.example.ratelimiter.service;

import com.example.ratelimiter.model.RateLimitType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 限流服务实现类
 *
 * 使用Redis + Lua脚本实现分布式限流，保证原子性
 *
 * @author Claude
 */
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimiterServiceImpl.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 固定窗口Lua脚本
     *
     * 逻辑：
     * 1. 获取当前计数
     * 2. 如果不存在或小于限制，则增加计数
     * 3. 如果是新key，设置过期时间
     */
    private static final String FIXED_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local period = tonumber(ARGV[2])

            local current = redis.call('get', key)

            if current and tonumber(current) >= limit then
                return 0
            end

            current = redis.call('incr', key)

            if tonumber(current) == 1 then
                redis.call('expire', key, period)
            end

            return 1
            """;

    /**
     * 滑动窗口Lua脚本
     *
     * 使用Redis的ZSET实现
     * score为时间戳，member为请求ID
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local period = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])
            local request_id = ARGV[4]

            -- 删除过期的记录
            local expire_time = current_time - period * 1000
            redis.call('zremrangebyscore', key, 0, expire_time)

            -- 获取当前窗口内的请求数
            local current_count = redis.call('zcard', key)

            if current_count >= limit then
                return 0
            end

            -- 添加当前请求
            redis.call('zadd', key, current_time, request_id)

            -- 设置key的过期时间
            redis.call('expire', key, period)

            return 1
            """;

    /**
     * 令牌桶Lua脚本
     *
     * 使用两个字段：
     * - tokens: 当前令牌数
     * - last_time: 上次更新时间
     */
    private static final String TOKEN_BUCKET_SCRIPT = """
            local tokens_key = KEYS[1]
            local time_key = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local tokens_per_second = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])

            -- 获取当前令牌数和上次更新时间
            local last_tokens = tonumber(redis.call('get', tokens_key)) or capacity
            local last_time = tonumber(redis.call('get', time_key)) or current_time

            -- 计算新增的令牌数
            local delta = math.max(0, current_time - last_time)
            local new_tokens = math.min(capacity, last_tokens + delta * tokens_per_second / 1000)

            -- 检查是否有足够的令牌
            if new_tokens < 1 then
                -- 更新状态
                redis.call('set', tokens_key, new_tokens)
                redis.call('set', time_key, current_time)
                redis.call('expire', tokens_key, 3600)
                redis.call('expire', time_key, 3600)
                return 0
            end

            -- 消耗一个令牌
            new_tokens = new_tokens - 1

            -- 更新状态
            redis.call('set', tokens_key, new_tokens)
            redis.call('set', time_key, current_time)
            redis.call('expire', tokens_key, 3600)
            redis.call('expire', time_key, 3600)

            return 1
            """;

    /**
     * 漏桶Lua脚本
     *
     * 使用两个字段：
     * - water: 当前水量
     * - last_time: 上次漏水时间
     */
    private static final String LEAKY_BUCKET_SCRIPT = """
            local water_key = KEYS[1]
            local time_key = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2])
            local current_time = tonumber(ARGV[3])

            -- 获取当前水量和上次漏水时间
            local last_water = tonumber(redis.call('get', water_key)) or 0
            local last_leak_time = tonumber(redis.call('get', time_key)) or current_time

            -- 计算漏掉的水量
            local delta = math.max(0, current_time - last_leak_time)
            local leaked_water = delta * rate / 1000
            local current_water = math.max(0, last_water - leaked_water)

            -- 检查是否还能加水
            if current_water >= capacity then
                -- 更新状态
                redis.call('set', water_key, current_water)
                redis.call('set', time_key, current_time)
                redis.call('expire', water_key, 3600)
                redis.call('expire', time_key, 3600)
                return 0
            end

            -- 加一滴水
            current_water = current_water + 1

            -- 更新状态
            redis.call('set', water_key, current_water)
            redis.call('set', time_key, current_time)
            redis.call('expire', water_key, 3600)
            redis.call('expire', time_key, 3600)

            return 1
            """;

    public RateLimiterServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 为 key 添加 Redis Hash Tag，确保同一限流维度的所有派生 key 落在同一 slot
     * 例如：rate_limit:api:test → {rate_limit:api:test}
     * 已包含 Hash Tag 的 key 不会重复包装
     */
    private String wrapWithHashTag(String key) {
        if (key.contains("{") && key.contains("}")) {
            return key;
        }
        return "{" + key + "}";
    }

    @Override
    public boolean tryAcquire(String key, long limit, long period, RateLimitType type) {
        return switch (type) {
            case FIXED_WINDOW -> tryAcquireFixedWindow(key, limit, period);
            case SLIDING_WINDOW -> tryAcquireSlidingWindow(key, limit, period);
            case TOKEN_BUCKET -> tryAcquireTokenBucket(key, limit, (double) limit / period);
            case LEAKY_BUCKET -> tryAcquireLeakyBucket(key, limit, (double) limit / period);
        };
    }

    @Override
    public boolean tryAcquireFixedWindow(String key, long limit, long period) {
        try {
            String hashTagKey = wrapWithHashTag(key);
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(FIXED_WINDOW_SCRIPT, Long.class);
            List<String> keys = Collections.singletonList(hashTagKey);

            Long result = redisTemplate.execute(
                    script,
                    keys,
                    String.valueOf(limit),
                    String.valueOf(period)
            );

            boolean allowed = result != null && result == 1;
            logger.debug("固定窗口限流 - key: {}, limit: {}, period: {}s, result: {}",
                    key, limit, period, allowed ? "允许" : "拒绝");

            return allowed;
        } catch (Exception e) {
            logger.error("固定窗口限流失败: {}", e.getMessage(), e);
            // 限流失败时默认放行，避免影响业务
            return true;
        }
    }

    @Override
    public boolean tryAcquireSlidingWindow(String key, long limit, long period) {
        try {
            String hashTagKey = wrapWithHashTag(key);
            long currentTime = System.currentTimeMillis();
            String requestId = currentTime + "_" + Thread.currentThread().getId();

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(SLIDING_WINDOW_SCRIPT, Long.class);
            List<String> keys = Collections.singletonList(hashTagKey);

            Long result = redisTemplate.execute(
                    script,
                    keys,
                    String.valueOf(limit),
                    String.valueOf(period),
                    String.valueOf(currentTime),
                    requestId
            );

            boolean allowed = result != null && result == 1;
            logger.debug("滑动窗口限流 - key: {}, limit: {}, period: {}s, result: {}",
                    key, limit, period, allowed ? "允许" : "拒绝");

            return allowed;
        } catch (Exception e) {
            logger.error("滑动窗口限流失败: {}", e.getMessage(), e);
            return true;
        }
    }

    @Override
    public boolean tryAcquireTokenBucket(String key, long capacity, double tokensPerSecond) {
        try {
            String hashTagKey = wrapWithHashTag(key);
            long currentTime = System.currentTimeMillis();

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(TOKEN_BUCKET_SCRIPT, Long.class);
            List<String> keys = Arrays.asList(hashTagKey + ":tokens", hashTagKey + ":last_time");

            Long result = redisTemplate.execute(
                    script,
                    keys,
                    String.valueOf(capacity),
                    String.valueOf(tokensPerSecond),
                    String.valueOf(currentTime)
            );

            boolean allowed = result != null && result == 1;
            logger.debug("令牌桶限流 - key: {}, capacity: {}, rate: {}/s, result: {}",
                    key, capacity, tokensPerSecond, allowed ? "允许" : "拒绝");

            return allowed;
        } catch (Exception e) {
            logger.error("令牌桶限流失败: {}", e.getMessage(), e);
            return true;
        }
    }

    @Override
    public boolean tryAcquireLeakyBucket(String key, long capacity, double rate) {
        try {
            String hashTagKey = wrapWithHashTag(key);
            long currentTime = System.currentTimeMillis();

            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LEAKY_BUCKET_SCRIPT, Long.class);
            List<String> keys = Arrays.asList(hashTagKey + ":water", hashTagKey + ":last_leak_time");

            Long result = redisTemplate.execute(
                    script,
                    keys,
                    String.valueOf(capacity),
                    String.valueOf(rate),
                    String.valueOf(currentTime)
            );

            boolean allowed = result != null && result == 1;
            logger.debug("漏桶限流 - key: {}, capacity: {}, rate: {}/s, result: {}",
                    key, capacity, rate, allowed ? "允许" : "拒绝");

            return allowed;
        } catch (Exception e) {
            logger.error("漏桶限流失败: {}", e.getMessage(), e);
            return true;
        }
    }

    @Override
    public long getRemainingQuota(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                return Long.parseLong(value);
            }
            return 0;
        } catch (Exception e) {
            logger.error("获取剩余配额失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public void reset(String key) {
        reset(key, 1);
    }

    @Override
    public void reset(String key, int shardCount) {
        try {
            List<String> keysToDelete = new java.util.ArrayList<>();
            // 收集所有分片的key（shardCount=1时只处理原始key）
            for (int i = 0; i < shardCount; i++) {
                String shardKey = shardCount > 1 ? key + ":shard:" + i : key;
                String hashTagKey = wrapWithHashTag(shardKey);
                keysToDelete.addAll(Arrays.asList(
                        hashTagKey,
                        hashTagKey + ":tokens",
                        hashTagKey + ":last_time",
                        hashTagKey + ":water",
                        hashTagKey + ":last_leak_time"
                ));
            }
            redisTemplate.delete(keysToDelete);
            logger.info("重置限流计数 - key: {}, shardCount: {}, 已删除 {} 个相关key",
                    key, shardCount, keysToDelete.size());
        } catch (Exception e) {
            logger.error("重置限流计数失败: {}", e.getMessage(), e);
        }
    }
}
