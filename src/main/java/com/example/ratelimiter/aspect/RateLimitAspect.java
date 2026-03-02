package com.example.ratelimiter.aspect;

import com.example.ratelimiter.annotation.RateLimit;
import com.example.ratelimiter.entity.RateLimitConfig;
import com.example.ratelimiter.exception.RateLimitException;
import com.example.ratelimiter.model.RateLimitType;
import com.example.ratelimiter.service.RateLimitConfigService;
import com.example.ratelimiter.service.RateLimiterService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 限流AOP切面
 *
 * 拦截带有@RateLimit注解的方法，执行限流逻辑
 *
 * @author Claude
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiterService rateLimiterService;
    private final RateLimitConfigService rateLimitConfigService;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    private record EffectiveRateLimitConfig(
            RateLimitType type, long limit, long period,
            long capacity, double tokensPerSecond, String message
    ) {}

    public RateLimitAspect(RateLimiterService rateLimiterService,
                           RateLimitConfigService rateLimitConfigService) {
        this.rateLimiterService = rateLimiterService;
        this.rateLimitConfigService = rateLimitConfigService;
    }

    /**
     * 环绕通知：拦截@RateLimit注解的方法
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        // 获取注解原始key（SpEL解析前），用于DB查找
        String rawKey = getRawKey(point, rateLimit);

        // 解析生效配置：DB优先，注解回退
        EffectiveRateLimitConfig effectiveConfig = resolveEffectiveConfig(rawKey, rateLimit);

        // 构建实际的限流key（含SpEL解析和参数处理）
        String resolvedKey = buildKey(point, rateLimit);

        logger.debug("限流检查开始 - key: {}, type: {}, config-source: {}",
                resolvedKey, effectiveConfig.type(),
                rateLimitConfigService.getActiveConfig(rawKey) != null ? "DB" : "annotation");

        // 执行限流检查
        boolean allowed = checkRateLimitWithConfig(resolvedKey, effectiveConfig);

        if (!allowed) {
            logger.warn("请求被限流 - key: {}, type: {}", resolvedKey, effectiveConfig.type());

            throw new RateLimitException(
                    effectiveConfig.message(),
                    resolvedKey,
                    effectiveConfig.limit(),
                    effectiveConfig.period()
            );
        }

        logger.debug("限流检查通过 - key: {}", resolvedKey);

        // 继续执行原方法
        return point.proceed();
    }

    /**
     * 获取注解原始key（SpEL解析前），用于DB配置查找
     */
    private String getRawKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        String key = rateLimit.key();
        if (key == null || key.isEmpty()) {
            MethodSignature signature = (MethodSignature) point.getSignature();
            key = signature.getDeclaringTypeName() + ":" + signature.getName();
        }
        return key;
    }

    /**
     * 解析生效配置：DB优先，注解回退
     */
    private EffectiveRateLimitConfig resolveEffectiveConfig(String rawKey, RateLimit rateLimit) {
        RateLimitConfig dbConfig = rateLimitConfigService.getActiveConfig(rawKey);

        if (dbConfig != null) {
            RateLimitType type = RateLimitType.valueOf(dbConfig.getType());
            long limit = dbConfig.getLimitCount() != null ? dbConfig.getLimitCount() : rateLimit.limit();
            long period = dbConfig.getPeriod() != null ? dbConfig.getPeriod() : rateLimit.period();
            long capacity = dbConfig.getCapacity() != null ? dbConfig.getCapacity() : rateLimit.capacity();
            double tokensPerSecond = dbConfig.getTokensPerSecond() != null ? dbConfig.getTokensPerSecond() : rateLimit.tokensPerSecond();
            String message = dbConfig.getMessage() != null ? dbConfig.getMessage() : rateLimit.message();

            return new EffectiveRateLimitConfig(type, limit, period, capacity, tokensPerSecond, message);
        }

        return new EffectiveRateLimitConfig(
                rateLimit.type(), rateLimit.limit(), rateLimit.period(),
                rateLimit.capacity(), rateLimit.tokensPerSecond(), rateLimit.message()
        );
    }

    /**
     * 构建限流key
     *
     * 支持：
     * 1. 固定key
     * 2. SpEL表达式（如：#{#userId}）
     * 3. 基于方法参数的key
     * 4. 基于IP的key
     */
    private String buildKey(ProceedingJoinPoint point, RateLimit rateLimit) {
        String key = rateLimit.key();

        // 如果key为空，使用方法签名作为key
        if (key == null || key.isEmpty()) {
            MethodSignature signature = (MethodSignature) point.getSignature();
            key = signature.getDeclaringTypeName() + ":" + signature.getName();
        }

        // 解析SpEL表达式
        if (key.contains("#")) {
            key = parseSpelKey(key, point);
        }

        // 如果使用参数作为key的一部分
        if (rateLimit.useParams()) {
            Object[] args = point.getArgs();
            if (args != null && args.length > 0) {
                key = key + ":" + Arrays.hashCode(args);
            }
        }

        // 添加全局前缀
        return "rate_limit:" + key;
    }

    /**
     * 解析SpEL表达式
     *
     * 示例：
     * - "api:user:#{#userId}" -> "api:user:123"
     * - "api:order:#{#order.id}" -> "api:order:456"
     */
    private String parseSpelKey(String key, ProceedingJoinPoint point) {
        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();

            // 获取方法参数名
            String[] paramNames = nameDiscoverer.getParameterNames(method);
            Object[] args = point.getArgs();

            // 创建SpEL上下文
            EvaluationContext context = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }

            // 解析SpEL表达式中的占位符
            while (key.contains("#{")) {
                int start = key.indexOf("#{");
                int end = key.indexOf("}", start);
                if (end == -1) {
                    break;
                }

                String expression = key.substring(start + 2, end);
                Expression exp = parser.parseExpression(expression);
                Object value = exp.getValue(context);

                key = key.substring(0, start) + value + key.substring(end + 1);
            }

            return key;
        } catch (Exception e) {
            logger.error("解析SpEL表达式失败: {}", e.getMessage(), e);
            return key;
        }
    }

    /**
     * 使用生效配置执行限流检查
     */
    private boolean checkRateLimitWithConfig(String key, EffectiveRateLimitConfig config) {
        return switch (config.type()) {
            case FIXED_WINDOW -> rateLimiterService.tryAcquireFixedWindow(
                    key, config.limit(), config.period()
            );
            case SLIDING_WINDOW -> rateLimiterService.tryAcquireSlidingWindow(
                    key, config.limit(), config.period()
            );
            case TOKEN_BUCKET -> rateLimiterService.tryAcquireTokenBucket(
                    key, config.capacity(), config.tokensPerSecond()
            );
            case LEAKY_BUCKET -> rateLimiterService.tryAcquireLeakyBucket(
                    key, config.capacity(), config.tokensPerSecond()
            );
        };
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();

                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }

                // 如果是多级代理，取第一个IP
                if (ip != null && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }

                return ip;
            }
        } catch (Exception e) {
            logger.warn("获取客户端IP失败: {}", e.getMessage());
        }

        return "unknown";
    }
}
