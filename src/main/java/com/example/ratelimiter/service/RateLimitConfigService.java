package com.example.ratelimiter.service;

import com.example.ratelimiter.entity.RateLimitConfig;
import com.example.ratelimiter.mapper.RateLimitConfigMapper;
import com.example.ratelimiter.model.RateLimitType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfigService.class);

    private final RateLimitConfigMapper configMapper;
    private final ConcurrentHashMap<String, RateLimitConfig> cache = new ConcurrentHashMap<>();

    public RateLimitConfigService(RateLimitConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @PostConstruct
    public void init() {
        refreshCache();
    }

    @Scheduled(fixedRate = 60000)
    public void scheduledRefresh() {
        refreshCache();
    }

    public void refreshCache() {
        try {
            List<RateLimitConfig> configs = configMapper.findAll();
            ConcurrentHashMap<String, RateLimitConfig> newCache = new ConcurrentHashMap<>();
            for (RateLimitConfig config : configs) {
                if (Boolean.TRUE.equals(config.getEnabled())) {
                    newCache.put(config.getLimitKey(), config);
                }
            }
            cache.clear();
            cache.putAll(newCache);
            logger.debug("限流配置缓存已刷新，共 {} 条有效配置", newCache.size());
        } catch (Exception e) {
            logger.error("刷新限流配置缓存失败，保持旧缓存: {}", e.getMessage());
        }
    }

    public RateLimitConfig getActiveConfig(String limitKey) {
        return cache.get(limitKey);
    }

    public List<RateLimitConfig> findAll() {
        return configMapper.findAll();
    }

    public RateLimitConfig findById(Long id) {
        return configMapper.findById(id);
    }

    public RateLimitConfig findByLimitKey(String limitKey) {
        return configMapper.findByLimitKey(limitKey);
    }

    public RateLimitConfig create(RateLimitConfig config) {
        validateType(config.getType());
        if (configMapper.existsByLimitKey(config.getLimitKey()) > 0) {
            throw new IllegalArgumentException("限流key已存在: " + config.getLimitKey());
        }
        configMapper.insert(config);
        if (Boolean.TRUE.equals(config.getEnabled())) {
            cache.put(config.getLimitKey(), config);
        }
        logger.info("创建限流配置: key={}, type={}", config.getLimitKey(), config.getType());
        return configMapper.findById(config.getId());
    }

    public RateLimitConfig update(Long id, RateLimitConfig config) {
        validateType(config.getType());
        RateLimitConfig existing = configMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("限流配置不存在: id=" + id);
        }
        config.setId(id);
        config.setLimitKey(existing.getLimitKey());
        configMapper.update(config);

        if (Boolean.TRUE.equals(config.getEnabled())) {
            RateLimitConfig updated = configMapper.findById(id);
            cache.put(updated.getLimitKey(), updated);
        } else {
            cache.remove(existing.getLimitKey());
        }
        logger.info("更新限流配置: id={}, key={}", id, existing.getLimitKey());
        return configMapper.findById(id);
    }

    public void deleteById(Long id) {
        RateLimitConfig existing = configMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("限流配置不存在: id=" + id);
        }
        configMapper.deleteById(id);
        cache.remove(existing.getLimitKey());
        logger.info("删除限流配置: id={}, key={}", id, existing.getLimitKey());
    }

    private void validateType(String type) {
        try {
            RateLimitType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的算法类型: " + type
                    + "，可选值: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET");
        }
    }
}
