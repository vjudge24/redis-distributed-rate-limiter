package com.example.ratelimiter.grpc;

import com.example.ratelimiter.grpc.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * gRPC 配置中心客户端
 *
 * 职责：
 * 1. 与 gRPC Server 建立长连接，订阅配置变更通知（Watch）
 * 2. 收到通知后，主动调用 FetchAllConfigs 拉取最新配置
 * 3. 连接断开时自动重连
 */
@Component
public class ConfigCenterGrpcClient {

    private static final Logger logger = LoggerFactory.getLogger(ConfigCenterGrpcClient.class);

    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    @Value("${grpc.client.target:localhost:9090}")
    private String target;

    private ManagedChannel channel;
    private RateLimitConfigCenterGrpc.RateLimitConfigCenterStub asyncStub;
    private RateLimitConfigCenterGrpc.RateLimitConfigCenterBlockingStub blockingStub;

    /** 收到变更通知后的回调，由 RateLimitConfigService 注册 */
    private Consumer<ConfigChangeEvent> onChangeCallback;

    private volatile boolean running = true;

    /**
     * 注册配置变更回调
     */
    public void setOnChangeCallback(Consumer<ConfigChangeEvent> callback) {
        this.onChangeCallback = callback;
    }

    /**
     * 启动客户端：建立连接 + 订阅 Watch
     */
    public void connect() {
        channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();
        asyncStub = RateLimitConfigCenterGrpc.newStub(channel);
        blockingStub = RateLimitConfigCenterGrpc.newBlockingStub(channel);

        logger.info("gRPC 客户端已连接到配置中心: {}, instanceId: {}", target, instanceId);

        startWatch();
    }

    /**
     * 订阅配置变更通知（Server-Streaming 长连接）
     */
    private void startWatch() {
        WatchRequest request = WatchRequest.newBuilder()
                .setInstanceId(instanceId)
                .build();

        asyncStub.watch(request, new StreamObserver<>() {
            @Override
            public void onNext(ConfigChangeEvent event) {
                logger.info("收到配置变更通知: type={}, key={}, timestamp={}",
                        event.getChangeType(), event.getLimitKey(), event.getTimestamp());

                // 触发回调，由 RateLimitConfigService 处理（拉取最新配置）
                if (onChangeCallback != null) {
                    onChangeCallback.accept(event);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.warn("Watch 连接异常: {}，将在 5 秒后重连", t.getMessage());
                scheduleReconnect();
            }

            @Override
            public void onCompleted() {
                logger.info("Watch 连接已关闭，将在 5 秒后重连");
                scheduleReconnect();
            }
        });

        logger.info("已订阅配置变更通知, instanceId: {}", instanceId);
    }

    /**
     * 主动拉取全量配置（收到通知后调用）
     */
    public FetchResponse fetchAllConfigs() {
        FetchRequest request = FetchRequest.newBuilder()
                .setInstanceId(instanceId)
                .build();

        FetchResponse response = blockingStub.fetchAllConfigs(request);
        logger.debug("已拉取 {} 条配置", response.getConfigsCount());
        return response;
    }

    /**
     * 断线重连
     */
    private void scheduleReconnect() {
        if (!running) return;

        Thread reconnectThread = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
                if (running) {
                    logger.info("尝试重新订阅 Watch...");
                    startWatch();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "grpc-reconnect");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (channel != null) {
            channel.shutdown();
            logger.info("gRPC 客户端已断开");
        }
    }

    public String getInstanceId() {
        return instanceId;
    }
}
