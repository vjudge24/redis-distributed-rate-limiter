package com.example.ratelimiter.controller;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.model.RateLimitType;
import com.example.ratelimiter.model.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 限流测试控制器
 *
 * 提供各种限流算法的测试端点
 *
 * @author Claude
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final Logger logger = LoggerFactory.getLogger(TestController.class);

    /**
     * 固定窗口限流测试
     *
     * 限制：每60秒最多5次请求
     *
     * 测试方法：
     * 快速刷新5次，第6次会被限流
     */
    @GetMapping("/fixed-window")
    @RateLimit(
            key = "test:fixed-window",
            limit = 5,
            period = 60,
            type = RateLimitType.FIXED_WINDOW,
            message = "固定窗口限流：每分钟最多5次请求"
    )
    public Result<Map<String, Object>> testFixedWindow() {
        logger.info("固定窗口限流测试 - 请求通过");

        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", "固定窗口计数器");
        data.put("description", "每60秒最多5次请求");
        data.put("timestamp", LocalDateTime.now());
        data.put("tip", "快速刷新5次，第6次会被限流");

        return Result.success("请求成功", data);
    }

    /**
     * 滑动窗口限流测试
     *
     * 限制：每30秒最多10次请求
     *
     * 测试方法：
     * 快速刷新10次，第11次会被限流
     */
    @GetMapping("/sliding-window")
    @RateLimit(
            key = "test:sliding-window",
            limit = 10,
            period = 30,
            type = RateLimitType.SLIDING_WINDOW,
            message = "滑动窗口限流：每30秒最多10次请求"
    )
    public Result<Map<String, Object>> testSlidingWindow() {
        logger.info("滑动窗口限流测试 - 请求通过");

        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", "滑动窗口计数器");
        data.put("description", "每30秒最多10次请求");
        data.put("timestamp", LocalDateTime.now());
        data.put("tip", "比固定窗口更平滑，解决了临界问题");

        return Result.success("请求成功", data);
    }

    /**
     * 令牌桶限流测试
     *
     * 桶容量：10个令牌
     * 令牌生成速率：每秒2个令牌
     *
     * 测试方法：
     * 快速请求10次消耗完令牌，然后等待令牌恢复
     */
    @GetMapping("/token-bucket")
    @RateLimit(
            key = "test:token-bucket",
            type = RateLimitType.TOKEN_BUCKET,
            capacity = 10,
            tokensPerSecond = 2.0,
            message = "令牌桶限流：桶容量10，每秒生成2个令牌"
    )
    public Result<Map<String, Object>> testTokenBucket() {
        logger.info("令牌桶限流测试 - 请求通过");

        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", "令牌桶算法");
        data.put("capacity", 10);
        data.put("tokensPerSecond", 2.0);
        data.put("timestamp", LocalDateTime.now());
        data.put("tip", "可以应对突发流量，最多可以连续请求10次");

        return Result.success("请求成功", data);
    }

    /**
     * 漏桶限流测试
     *
     * 桶容量：5
     * 漏出速率：每秒1个请求
     *
     * 测试方法：
     * 快速请求5次填满漏桶，第6次被限流
     */
    @GetMapping("/leaky-bucket")
    @RateLimit(
            key = "test:leaky-bucket",
            type = RateLimitType.LEAKY_BUCKET,
            capacity = 5,
            tokensPerSecond = 1.0,
            message = "漏桶限流：桶容量5，每秒漏出1个请求"
    )
    public Result<Map<String, Object>> testLeakyBucket() {
        logger.info("漏桶限流测试 - 请求通过");

        Map<String, Object> data = new HashMap<>();
        data.put("algorithm", "漏桶算法");
        data.put("capacity", 5);
        data.put("leakRate", 1.0);
        data.put("timestamp", LocalDateTime.now());
        data.put("tip", "强制限制请求速率，流量更平滑");

        return Result.success("请求成功", data);
    }

    /**
     * 基于用户ID的限流测试（SpEL表达式）
     *
     * 限制：每个用户每60秒最多3次请求
     *
     * 测试方法：
     * 使用不同的userId测试，每个用户独立限流
     */
    @GetMapping("/user/{userId}")
    @RateLimit(
            key = "test:user:#{#userId}",
            limit = 3,
            period = 60,
            type = RateLimitType.SLIDING_WINDOW,
            message = "用户限流：每个用户每分钟最多3次请求"
    )
    public Result<Map<String, Object>> testUserRateLimit(@PathVariable Long userId) {
        logger.info("用户限流测试 - userId: {}, 请求通过", userId);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("description", "每个用户独立限流");
        data.put("limit", "3次/分钟");
        data.put("timestamp", LocalDateTime.now());
        data.put("tip", "使用SpEL表达式实现动态key");

        return Result.success("请求成功", data);
    }

    /**
     * 基于IP的限流测试
     *
     * 限制：每个IP每10秒最多5次请求
     */
    @GetMapping("/ip-limit")
    @RateLimit(
            key = "test:ip",
            limit = 5,
            period = 10,
            type = RateLimitType.SLIDING_WINDOW,
            useParams = true,
            message = "IP限流：每个IP每10秒最多5次请求"
    )
    public Result<Map<String, Object>> testIpRateLimit() {
        logger.info("IP限流测试 - 请求通过");

        Map<String, Object> data = new HashMap<>();
        data.put("description", "基于IP地址的限流");
        data.put("limit", "5次/10秒");
        data.put("timestamp", LocalDateTime.now());

        return Result.success("请求成功", data);
    }

    /**
     * 无限流接口（用于对比）
     */
    @GetMapping("/no-limit")
    public Result<Map<String, Object>> testNoLimit() {
        logger.info("无限流接口 - 请求通过");

        Map<String, Object> data = new HashMap<>();
        data.put("description", "此接口没有限流");
        data.put("timestamp", LocalDateTime.now());

        return Result.success("请求成功", data);
    }

    /**
     * 获取系统信息
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("project", "Redis Distributed Rate Limiter");
        info.put("version", "1.0.0");
        info.put("jdk", System.getProperty("java.version"));
        info.put("timestamp", LocalDateTime.now());

        Map<String, String> algorithms = new HashMap<>();
        algorithms.put("fixed-window", "固定窗口计数器");
        algorithms.put("sliding-window", "滑动窗口计数器");
        algorithms.put("token-bucket", "令牌桶算法");
        algorithms.put("leaky-bucket", "漏桶算法");
        info.put("algorithms", algorithms);

        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("GET /api/test/fixed-window", "固定窗口限流测试");
        endpoints.put("GET /api/test/sliding-window", "滑动窗口限流测试");
        endpoints.put("GET /api/test/token-bucket", "令牌桶限流测试");
        endpoints.put("GET /api/test/leaky-bucket", "漏桶限流测试");
        endpoints.put("GET /api/test/user/{userId}", "用户限流测试");
        endpoints.put("GET /api/test/ip-limit", "IP限流测试");
        endpoints.put("GET /api/test/no-limit", "无限流测试");
        info.put("endpoints", endpoints);

        return Result.success("系统信息", info);
    }
}
