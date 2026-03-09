package com.example.ratelimiter.service;

import com.example.ratelimiter.entity.RateLimitConfig;
import com.example.ratelimiter.grpc.ConfigCenterGrpcClient;
import com.example.ratelimiter.grpc.ConfigCenterGrpcServer;
import com.example.ratelimiter.grpc.proto.ChangeType;
import com.example.ratelimiter.grpc.proto.FetchResponse;
import com.example.ratelimiter.grpc.proto.RateLimitConfigProto;
import com.example.ratelimiter.mapper.RateLimitConfigMapper;
import com.example.ratelimiter.model.RateLimitType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流配置服务
 *
 * 配置刷新机制（gRPC 配置中心模式）：
 * 1. 启动时：通过 gRPC FetchAllConfigs 拉取全量配置
 * 2. 运行时：通过 gRPC Watch 长连接监听变更通知
 * 3. 收到通知后：主动调用 FetchAllConfigs 拉取最新配置刷新本地缓存
 * 4. 配置变更时：通过 gRPC Server 向所有订阅实例推送通知
 */
@Service
public class RateLimitConfigService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfigService.class);

    private final RateLimitConfigMapper configMapper;
    private final ConfigCenterGrpcServer grpcServer;
    private final ConfigCenterGrpcClient grpcClient;

    private final ConcurrentHashMap<String, RateLimitConfig> cache = new ConcurrentHashMap<>();

    public RateLimitConfigService(RateLimitConfigMapper configMapper,
                                  ConfigCenterGrpcServer grpcServer,
                                  ConfigCenterGrpcClient grpcClient) {
        this.configMapper = configMapper;
        this.grpcServer = grpcServer;
        this.grpcClient = grpcClient;
    }

    @PostConstruct
    public void init() {
        // 1. 先从 DB 加载初始配置（gRPC 连接可能还没建立）
        refreshCacheFromDb();

        // 2. 注册变更回调：收到 gRPC 通知后，主动拉取最新配置
        grpcClient.setOnChangeCallback(event -> {
            logger.info("收到配置变更通知，开始拉取最新配置: type={}, key={}",
                    event.getChangeType(), event.getLimitKey());
            refreshCacheFromGrpc();
        });

        // 3. 建立 gRPC 长连接，订阅 Watch
        grpcClient.connect();
    }

    /**
     * 从 gRPC 配置中心拉取全量配置，刷新本地缓存
     */
    private void refreshCacheFromGrpc() {
        try {
            FetchResponse response = grpcClient.fetchAllConfigs();

            ConcurrentHashMap<String, RateLimitConfig> newCache = new ConcurrentHashMap<>();
            for (RateLimitConfigProto proto : response.getConfigsList()) {
                RateLimitConfig config = fromProto(proto);
                newCache.put(config.getLimitKey(), config);
            }

            cache.clear();
            cache.putAll(newCache);
            logger.info("限流配置缓存已通过 gRPC 刷新，共 {} 条有效配置", newCache.size());
        } catch (Exception e) {
            logger.error("通过 gRPC 刷新配置失败，保持旧缓存: {}", e.getMessage());
        }
    }

    /**
     * 直接从 DB 加载配置（启动时使用）
     */
    private void refreshCacheFromDb() {
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
            logger.debug("限流配置缓存已从 DB 加载，共 {} 条有效配置", newCache.size());
        } catch (Exception e) {
            logger.error("从 DB 加载限流配置失败: {}", e.getMessage());
        }
    }

    /**
     * 手动刷新（REST API 触发，同时通知所有实例）
     */
    public void refreshCache() {
        refreshCacheFromDb();
        grpcServer.notifyConfigChange(ChangeType.FULL_REFRESH, null);
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

        // 推送变更通知给所有订阅实例
        grpcServer.notifyConfigChange(ChangeType.UPDATED, config.getLimitKey());

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

        // 推送变更通知
        grpcServer.notifyConfigChange(ChangeType.UPDATED, existing.getLimitKey());

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

        // 推送删除通知
        grpcServer.notifyConfigChange(ChangeType.DELETED, existing.getLimitKey());
    }

    private void validateType(String type) {
        try {
            RateLimitType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的算法类型: " + type
                    + "，可选值: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET");
        }
    }

    private RateLimitConfig fromProto(RateLimitConfigProto proto) {
        RateLimitConfig config = new RateLimitConfig();
        config.setId(proto.getId());
        config.setLimitKey(proto.getLimitKey());
        config.setType(proto.getType());
        config.setLimitCount(proto.getLimitCount() > 0 ? proto.getLimitCount() : null);
        config.setPeriod(proto.getPeriod() > 0 ? proto.getPeriod() : null);
        config.setCapacity(proto.getCapacity() > 0 ? proto.getCapacity() : null);
        config.setTokensPerSecond(proto.getTokensPerSecond() > 0 ? proto.getTokensPerSecond() : null);
        config.setMessage(proto.getMessage().isEmpty() ? null : proto.getMessage());
        config.setEnabled(proto.getEnabled());
        config.setDescription(proto.getDescription().isEmpty() ? null : proto.getDescription());
        return config;
    }
}
