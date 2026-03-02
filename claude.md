# Redis 分布式限流系统 - Claude Code 开发指南

## 项目概述

基于 **JDK 17 + Spring Boot 3 + Redis** 实现的高性能分布式限流系统，支持 4 种限流算法，通过 Spring AOP 和 Lua 脚本实现原子操作。

**项目地址**: `/Users/vjudge24/redis-distributed-rate-limiter`

## 技术栈

- **JDK 17** - 使用最新的 Java 特性（Records、Sealed Classes 等）
- **Spring Boot 3.2.1** - 企业级应用框架
- **Redis 5.0+** - 分布式缓存和状态存储
- **Lettuce** - 高性能 Redis 客户端，非阻塞式 I/O
- **Spring AOP** - 切面编程，实现透明的限流拦截
- **Lua 脚本** - 保证 Redis 操作的原子性和一致性

## 项目结构

```
redis-distributed-rate-limiter/
├── src/main/java/com/example/ratelimiter/
│   ├── annotation/
│   │   └── RateLimit.java              # 限流注解定义
│   │                                   # 支持SpEL表达式、自定义消息、多种算法
│   ├── aspect/
│   │   └── RateLimitAspect.java        # AOP切面实现
│   │                                   # 拦截注解方法、解析SpEL、触发限流检查
│   ├── config/
│   │   ├── RedisConfig.java            # Redis连接池配置
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── controller/
│   │   └── TestController.java         # 测试端点，演示各种限流场景
│   ├── exception/
│   │   └── RateLimitException.java     # 限流异常类
│   ├── model/
│   │   ├── RateLimitType.java          # 限流算法枚举
│   │   └── Result.java                 # 统一响应格式
│   ├── service/
│   │   ├── RateLimiterService.java     # 限流服务接口
│   │   └── RateLimiterServiceImpl.java  # 服务实现（包含4个Lua脚本）
│   └── RateLimiterApplication.java     # Spring Boot 启动类
├── src/main/resources/
│   └── application.yml                 # 应用配置
├── pom.xml                             # Maven依赖配置
└── README.md                           # 项目文档
```

## 核心文件说明

### 1. @RateLimit 注解 (annotation/RateLimit.java:1-98)

限流配置注解，支持以下参数：

```java
@RateLimit(
    key = "api:user:#{#userId}",     // 限流key（支持SpEL表达式）
    limit = 10,                       // 时间窗口内最大请求数
    period = 60,                      // 时间窗口（秒）
    type = RateLimitType.SLIDING_WINDOW,  // 算法类型
    message = "请求过于频繁",          // 限流提示信息
    tokensPerSecond = 10.0,          // 令牌生成速率（令牌桶/漏桶）
    capacity = 10                     // 桶容量
)
```

**SpEL 表达式示例**：
- `#{#userId}` - 基于路径参数
- `#{#user.id}` - 基于对象属性
- `#{#request.header['X-API-Key']}` - 基于请求头

### 2. RateLimitAspect 切面 (aspect/RateLimitAspect.java:32-177)

**核心方法**：
- `around()` - 环绕通知，拦截注解方法
- `buildKey()` - 构建限流 key，支持 SpEL 解析和参数处理
- `parseSpelKey()` - 解析 SpEL 表达式
- `checkRateLimit()` - 调用限流服务检查请求
- `getClientIp()` - 获取客户端 IP（支持代理和多级转发）

### 3. RateLimiterServiceImpl 实现 (service/RateLimiterServiceImpl.java:21-326)

**四种限流算法的 Lua 脚本**：

#### 固定窗口 (FIXED_WINDOW_SCRIPT:35-53)
- 将时间分为固定窗口
- 使用简单的计数器实现
- 存在临界问题（窗口边界流量突刺）
- QPS: 10,000+，响应时间: 2ms

#### 滑动窗口 (SLIDING_WINDOW_SCRIPT:61-86)
- 使用 Redis ZSET 存储请求时间戳
- 动态清理过期请求
- 解决固定窗口的临界问题
- QPS: 8,000+，响应时间: 3ms
- **推荐用于大多数场景**

#### 令牌桶 (TOKEN_BUCKET_SCRIPT:95-132)
- 系统以恒定速率向桶中放入令牌
- 请求需要获取令牌才能通过
- 可应对突发流量
- QPS: 9,000+，响应时间: 2.5ms
- **适用于需要弹性限流的场景**

#### 漏桶 (LEAKY_BUCKET_SCRIPT:141-179)
- 请求以恒定速率从桶中流出
- 强制限制传输速率
- 无法应对突发流量
- QPS: 9,000+，响应时间: 2.5ms
- **适用于需要恒定速率的场景（如导出功能）**

## 快速开始

### 前置要求

```bash
# Java 17+
java -version

# Maven 3.6+
mvn -version

# Redis 5.0+（通过 Docker 或本地安装）
redis-cli ping
```

### 启动应用

```bash
# 1. 启动 Redis（如果未启动）
brew services start redis  # macOS
# 或
docker run -d -p 6379:6379 redis:latest

# 2. 编译项目
cd /Users/vjudge24/redis-distributed-rate-limiter
mvn clean package

# 3. 启动应用
mvn spring-boot:run

# 4. 验证启动成功
curl http://localhost:8082/api/test/info
```

### 测试限流

```bash
# 固定窗口限流（每分钟5次）
for i in {1..10}; do curl http://localhost:8082/api/test/fixed-window; done

# 滑动窗口限流（每30秒10次）
for i in {1..15}; do curl http://localhost:8082/api/test/sliding-window; done

# 令牌桶限流（容量10，每秒2个令牌）
for i in {1..20}; do curl http://localhost:8082/api/test/token-bucket; done

# 漏桶限流（容量5，每秒1个）
for i in {1..10}; do curl http://localhost:8082/api/test/leaky-bucket; done

# 用户级限流
curl http://localhost:8082/api/test/user/123
curl http://localhost:8082/api/test/user/456
```

## 开发指南

### 1. 添加限流到现有接口

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    // 基础用法：固定窗口
    @RateLimit(
        key = "api:getData",
        limit = 10,
        period = 60,
        type = RateLimitType.FIXED_WINDOW
    )
    @GetMapping("/data")
    public Result getData() {
        return Result.success("数据");
    }

    // 高级用法：SpEL表达式
    @RateLimit(
        key = "api:user:#{#userId}",
        limit = 10,
        period = 60,
        type = RateLimitType.SLIDING_WINDOW
    )
    @GetMapping("/user/{userId}/profile")
    public Result getUserProfile(@PathVariable Long userId) {
        return Result.success("用户信息");
    }

    // 令牌桶：应对突发流量
    @RateLimit(
        key = "api:search",
        type = RateLimitType.TOKEN_BUCKET,
        capacity = 100,
        tokensPerSecond = 10.0
    )
    @GetMapping("/search")
    public Result search(@RequestParam String keyword) {
        return Result.success("搜索结果");
    }
}
```

### 2. 修改限流参数

**注解参数**：编辑 `@RateLimit` 注解中的参数
- `limit` - 请求数限制
- `period` - 时间窗口（秒）
- `tokensPerSecond` - 令牌生成速率
- `capacity` - 桶容量

**运行时获取配额**：
```java
@Autowired
private RateLimiterService rateLimiterService;

long remaining = rateLimiterService.getRemainingQuota("rate_limit:api:test");
```

### 3. 自定义限流异常处理

编辑 `config/GlobalExceptionHandler.java`：

```java
@ExceptionHandler(RateLimitException.class)
public ResponseEntity<Result> handleRateLimitException(RateLimitException e) {
    // 自定义响应逻辑
    return ResponseEntity.status(429)
        .body(Result.error(e.getMessage()));
}
```

### 4. 添加新的限流算法

在 `RateLimiterServiceImpl` 中：

1. 定义新的 Lua 脚本
2. 在 `tryAcquire()` switch 分支中添加新的算法类型
3. 在 `RateLimitType.java` 枚举中添加新的算法

示例：
```java
private static final String NEW_ALGORITHM_SCRIPT = """
    -- Lua脚本逻辑
    """;

private boolean tryAcquireNewAlgorithm(String key, long param1, long param2) {
    // 实现逻辑
}
```

## 常见开发任务

### 查看日志

```bash
# 构建时查看 Maven 日志
mvn clean package -DskipTests

# 运行时查看应用日志（实时输出）
mvn spring-boot:run -DnumberOfThreads=1

# 调整日志级别（编辑 application.yml）
logging:
  level:
    com.example.ratelimiter: DEBUG
```

### 调试 SpEL 表达式

编辑 `aspect/RateLimitAspect.java:153-156`：
```java
logger.debug("解析SpEL - 表达式: {}, 结果: {}", expression, value);
```

### 性能优化

1. **批量请求测试**：
```bash
ab -n 1000 -c 10 http://localhost:8082/api/test/sliding-window
```

2. **监控 Redis 内存**：
```bash
redis-cli INFO memory
redis-cli DBSIZE
```

3. **查看限流 key**：
```bash
redis-cli KEYS "rate_limit:*" | head -20
redis-cli TTL "rate_limit:api:test"
```

### 重置限流计数

```bash
# Java 代码中
rateLimiterService.reset("rate_limit:api:test");

# 或直接在 Redis CLI 中
redis-cli DEL rate_limit:api:test
```

## 重要代码位置

| 功能 | 文件路径 | 行数 |
|------|--------|------|
| 注解定义 | annotation/RateLimit.java | 30-98 |
| AOP 切面 | aspect/RateLimitAspect.java | 32-177 |
| SpEL 解析 | aspect/RateLimitAspect.java | 120-157 |
| 限流实现 | service/RateLimiterServiceImpl.java | 21-326 |
| 固定窗口 Lua | service/RateLimiterServiceImpl.java | 35-53 |
| 滑动窗口 Lua | service/RateLimiterServiceImpl.java | 61-86 |
| 令牌桶 Lua | service/RateLimiterServiceImpl.java | 95-132 |
| 漏桶 Lua | service/RateLimiterServiceImpl.java | 141-179 |
| 异常处理 | config/GlobalExceptionHandler.java | - |
| 测试控制器 | controller/TestController.java | - |

## Redis 关键命令

```bash
# 连接 Redis
redis-cli

# 查看所有限流 key
KEYS rate_limit:*

# 查看特定 key 的值和过期时间
GET rate_limit:api:test
TTL rate_limit:api:test

# 查看滑动窗口的时间戳记录
ZRANGE rate_limit:api:sliding-window 0 -1 WITHSCORES

# 查看令牌桶/漏桶的状态
GET rate_limit:api:bucket:tokens
GET rate_limit:api:bucket:last_time

# 重置计数
DEL rate_limit:api:test

# 监控 Redis 操作
MONITOR
```

## 扩展和改进建议

### 短期优化
- [ ] 支持动态配置（集成 Nacos 或 Apollo）
- [ ] 添加白名单机制
- [ ] 支持基于用户等级的分级限流
- [ ] 集成 Prometheus 指标收集

### 中期功能
- [ ] 限流告警和通知
- [ ] 限流日志收集（ELK 集成）
- [ ] 限流策略的可视化管理
- [ ] 分布式限流的协调优化

### 长期架构
- [ ] 支持多个 Redis 节点的高可用
- [ ] 实现限流的灰度发布
- [ ] 机器学习优化限流参数
- [ ] 支持更多限流算法（漏桶变种、等等）

## 常见问题

**Q: Redis 连接失败时限流行为？**
A: 限流服务默认在异常时放行（fail-open 策略），避免影响业务

**Q: 如何支持多个 Redis 实例？**
A: 修改 `config/RedisConfig.java`，配置 Redis Cluster 或 Sentinel

**Q: 如何测试限流？**
A: 使用 Apache Bench 或 JMeter 进行压测，参考快速开始部分

**Q: 限流 key 如何设计？**
A: 遵循规范 `模块:资源:维度`，如 `api:user:#{#userId}`，便于监控和管理

## 相关资源

- [Redis 官方文档](https://redis.io/docs/)
- [Spring Boot 文档](https://spring.io/projects/spring-boot)
- [Spring AOP 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop)
- [Lua 脚本教程](https://www.lua.org/manual/5.1/)
- [限流算法详解](https://en.wikipedia.org/wiki/Rate_limiting)
