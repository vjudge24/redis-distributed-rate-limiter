package com.example.ratelimiter.grpc;

import com.example.ratelimiter.entity.RateLimitConfig;
import com.example.ratelimiter.grpc.proto.*;
import com.example.ratelimiter.mapper.RateLimitConfigMapper;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * gRPC 配置中心服务端
 *
 * 职责：
 * 1. 维护所有客户端的 Watch 长连接（Server-Streaming）
 * 2. 配置变更时，向所有订阅者推送 ConfigChangeEvent 通知
 * 3. 响应客户端的 FetchAllConfigs 拉取请求
 */
@Component
public class ConfigCenterGrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(ConfigCenterGrpcServer.class);

    private final RateLimitConfigMapper configMapper;
    private Server server;

    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    /**
     * 所有活跃的 Watch 订阅者
     * key: instanceId, value: StreamObserver（用于向该客户端推送事件）
     */
    private final ConcurrentHashMap<String, StreamObserver<ConfigChangeEvent>> watchers = new ConcurrentHashMap<>();

    public ConfigCenterGrpcServer(RateLimitConfigMapper configMapper) {
        this.configMapper = configMapper;
    }

    @PostConstruct
    public void start() throws IOException {
        server = ServerBuilder.forPort(grpcPort)
                .addService(new ConfigCenterServiceImpl())
                .build()
                .start();
        logger.info("gRPC 配置中心服务端已启动，端口: {}", grpcPort);
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            logger.info("gRPC 配置中心服务端已停止");
        }
    }

    /**
     * 向所有订阅者推送配置变更通知
     * 由 RateLimitConfigService 在配置变更时调用
     */
    public void notifyConfigChange(ChangeType changeType, String limitKey) {
        ConfigChangeEvent event = ConfigChangeEvent.newBuilder()
                .setChangeType(changeType)
                .setLimitKey(limitKey != null ? limitKey : "")
                .setTimestamp(System.currentTimeMillis())
                .build();

        List<String> deadWatchers = new CopyOnWriteArrayList<>();

        watchers.forEach((instanceId, observer) -> {
            try {
                observer.onNext(event);
                logger.debug("已推送配置变更通知到实例: {}, type: {}, key: {}",
                        instanceId, changeType, limitKey);
            } catch (Exception e) {
                logger.warn("推送通知失败，移除失效订阅者: {}, error: {}", instanceId, e.getMessage());
                deadWatchers.add(instanceId);
            }
        });

        deadWatchers.forEach(watchers::remove);

        logger.info("配置变更通知已推送，type: {}, key: {}, 活跃订阅者: {}",
                changeType, limitKey, watchers.size());
    }

    /**
     * gRPC 服务实现
     */
    private class ConfigCenterServiceImpl extends RateLimitConfigCenterGrpc.RateLimitConfigCenterImplBase {

        /**
         * Watch - Server-Streaming 长连接
         * 客户端调用后保持连接，Server 在配置变更时推送事件
         */
        @Override
        public void watch(WatchRequest request, StreamObserver<ConfigChangeEvent> responseObserver) {
            String instanceId = request.getInstanceId();
            logger.info("新的 Watch 订阅: instanceId={}", instanceId);

            // 注册订阅者
            watchers.put(instanceId, responseObserver);

            // 注意：不调用 onCompleted()，保持流开放
            // 当客户端断开时，通过 onError/onCompleted 回调清理
        }

        /**
         * FetchAllConfigs - 客户端收到变更通知后，主动拉取全量配置
         */
        @Override
        public void fetchAllConfigs(FetchRequest request, StreamObserver<FetchResponse> responseObserver) {
            String instanceId = request.getInstanceId();
            logger.debug("配置拉取请求: instanceId={}", instanceId);

            try {
                List<RateLimitConfig> configs = configMapper.findAll();

                FetchResponse.Builder responseBuilder = FetchResponse.newBuilder();
                for (RateLimitConfig config : configs) {
                    if (Boolean.TRUE.equals(config.getEnabled())) {
                        responseBuilder.addConfigs(toProto(config));
                    }
                }

                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();

                logger.debug("已返回 {} 条配置给实例: {}", responseBuilder.getConfigsCount(), instanceId);
            } catch (Exception e) {
                logger.error("拉取配置失败: {}", e.getMessage(), e);
                responseObserver.onError(e);
            }
        }

        private RateLimitConfigProto toProto(RateLimitConfig config) {
            RateLimitConfigProto.Builder builder = RateLimitConfigProto.newBuilder()
                    .setId(config.getId())
                    .setLimitKey(config.getLimitKey())
                    .setType(config.getType())
                    .setEnabled(Boolean.TRUE.equals(config.getEnabled()));

            if (config.getLimitCount() != null) builder.setLimitCount(config.getLimitCount());
            if (config.getPeriod() != null) builder.setPeriod(config.getPeriod());
            if (config.getCapacity() != null) builder.setCapacity(config.getCapacity());
            if (config.getTokensPerSecond() != null) builder.setTokensPerSecond(config.getTokensPerSecond());
            if (config.getMessage() != null) builder.setMessage(config.getMessage());
            if (config.getDescription() != null) builder.setDescription(config.getDescription());

            return builder.build();
        }
    }
}
