package com.example.ratelimiter.exception;

/**
 * 限流异常
 *
 * 当请求超过限流阈值时抛出此异常
 *
 * @author Claude
 */
public class RateLimitException extends RuntimeException {

    private final String key;
    private final long limit;
    private final long period;

    public RateLimitException(String message) {
        super(message);
        this.key = "";
        this.limit = 0;
        this.period = 0;
    }

    public RateLimitException(String message, String key, long limit, long period) {
        super(message);
        this.key = key;
        this.limit = limit;
        this.period = period;
    }

    public String getKey() {
        return key;
    }

    public long getLimit() {
        return limit;
    }

    public long getPeriod() {
        return period;
    }

    @Override
    public String toString() {
        return String.format("RateLimitException{key='%s', limit=%d, period=%ds, message='%s'}",
                key, limit, period, getMessage());
    }
}
