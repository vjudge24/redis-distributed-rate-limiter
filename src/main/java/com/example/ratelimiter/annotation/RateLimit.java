package com.example.ratelimiter.annotation;

import com.example.ratelimiter.model.RateLimitType;

import java.lang.annotation.*;

/**
 * 分布式限流注解
 *
 * 使用示例：
 * <pre>
 * {@code
 * @RateLimit(
 *     key = "api:getUserInfo",
 *     limit = 10,
 *     period = 60,
 *     type = RateLimitType.SLIDING_WINDOW
 * )
 * public Result getUserInfo(Long userId) {
 *     // 业务逻辑
 * }
 * }
 * </pre>
 *
 * @author Claude
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流key（支持SpEL表达式）
     *
     * 示例：
     * - "api:test" - 固定key
     * - "api:user:#{#userId}" - 基于参数的动态key
     * - "api:user:#{#user.id}" - 基于对象属性的key
     *
     * @return 限流key
     */
    String key() default "";

    /**
     * 限流次数
     *
     * @return 在指定时间窗口内允许的最大请求次数
     */
    long limit() default 10;

    /**
     * 时间窗口（秒）
     *
     * @return 限流时间窗口，单位：秒
     */
    long period() default 60;

    /**
     * 限流算法类型
     *
     * @return 使用的限流算法
     */
    RateLimitType type() default RateLimitType.SLIDING_WINDOW;

    /**
     * 限流失败时的提示信息
     *
     * @return 限流时返回的错误信息
     */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 是否使用参数作为限流key的一部分
     *
     * 如果为true，会将方法参数的hashCode作为key的一部分
     *
     * @return 是否使用参数
     */
    boolean useParams() default false;

    /**
     * 令牌桶算法 - 令牌生成速率（每秒生成的令牌数）
     *
     * 仅在type=TOKEN_BUCKET时生效
     *
     * @return 令牌生成速率
     */
    double tokensPerSecond() default 10.0;

    /**
     * 令牌桶算法 - 桶的最大容量
     *
     * 仅在type=TOKEN_BUCKET时生效
     *
     * @return 桶的最大容量
     */
    long capacity() default 10;

    /**
     * 热key分片数量
     *
     * 用于解决Redis集群模式下的热key问题。
     * 将单个限流key拆分为多个分片key，分散到不同的Redis节点。
     * 每个分片的限额 = 总限额 / 分片数。
     *
     * 设为1表示不分片（默认）。建议设为集群节点数的4-8倍（如3节点集群设为16）。
     *
     * @return 分片数量
     */
    int shardCount() default 1;
}
