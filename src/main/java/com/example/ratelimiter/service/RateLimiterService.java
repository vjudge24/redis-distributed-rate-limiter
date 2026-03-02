package com.example.ratelimiter.service;

import com.example.ratelimiter.model.RateLimitType;

/**
 * 限流服务接口
 *
 * @author Claude
 */
public interface RateLimiterService {

    /**
     * 尝试获取访问许可
     *
     * @param key    限流key
     * @param limit  限流次数
     * @param period 时间窗口（秒）
     * @param type   限流算法类型
     * @return true-允许访问，false-拒绝访问
     */
    boolean tryAcquire(String key, long limit, long period, RateLimitType type);

    /**
     * 固定窗口计数器算法
     *
     * @param key    限流key
     * @param limit  限流次数
     * @param period 时间窗口（秒）
     * @return true-允许访问，false-拒绝访问
     */
    boolean tryAcquireFixedWindow(String key, long limit, long period);

    /**
     * 滑动窗口计数器算法
     *
     * @param key    限流key
     * @param limit  限流次数
     * @param period 时间窗口（秒）
     * @return true-允许访问，false-拒绝访问
     */
    boolean tryAcquireSlidingWindow(String key, long limit, long period);

    /**
     * 令牌桶算法
     *
     * @param key              限流key
     * @param capacity         桶容量
     * @param tokensPerSecond  令牌生成速率（每秒）
     * @return true-允许访问，false-拒绝访问
     */
    boolean tryAcquireTokenBucket(String key, long capacity, double tokensPerSecond);

    /**
     * 漏桶算法
     *
     * @param key      限流key
     * @param capacity 桶容量
     * @param rate     漏出速率（每秒）
     * @return true-允许访问，false-拒绝访问
     */
    boolean tryAcquireLeakyBucket(String key, long capacity, double rate);

    /**
     * 获取剩余配额
     *
     * @param key 限流key
     * @return 剩余可用次数
     */
    long getRemainingQuota(String key);

    /**
     * 重置限流计数
     *
     * @param key 限流key
     */
    void reset(String key);
}
