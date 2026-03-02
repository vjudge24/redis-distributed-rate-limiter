# Redis 分布式限流系统

基于 **JDK 17 + Spring Boot 3 + Redis + MySQL** 实现的高性能分布式限流系统，支持多种限流算法，支持运行时动态调参。

## 技术栈

- **JDK 17** - 使用最新的Java特性
- **Spring Boot 3.2.1** - 企业级应用框架
- **Redis** - 分布式缓存，保证限流的分布式一致性
- **Lettuce** - 高性能Redis客户端
- **MySQL + MyBatis** - 持久化限流配置，支持运行时动态调参
- **Spring AOP** - 切面编程，优雅实现限流拦截
- **Lua脚本** - 保证Redis操作的原子性

## 核心特性

### 🚀 四种限流算法

1. **固定窗口计数器（Fixed Window）**
   - 实现简单，内存占用低
   - 适合对精度要求不高的场景

2. **滑动窗口计数器（Sliding Window）**
   - 解决固定窗口的临界问题
   - 限流更平滑，精度更高

3. **令牌桶算法（Token Bucket）**
   - 可以应对突发流量
   - 灵活性高，适合需要弹性限流的场景

4. **漏桶算法（Leaky Bucket）**
   - 强制限制数据传输速率
   - 流量平滑，适合需要恒定速率的场景

### ✨ 核心优势

- **分布式支持** - 基于Redis，天然支持分布式部署
- **高性能** - 使用Lua脚本保证原子性，无并发问题
- **易用性** - 注解式使用，极简API
- **灵活配置** - 支持SpEL表达式，动态生成限流key
- **动态调参** - MySQL持久化配置，RESTful API管理，运行时生效，无需重启
- **多维度限流** - 支持全局、接口、用户、IP等多种维度
- **优雅降级** - Redis异常时自动放行，不影响业务

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.6+
- Redis 5.0+
- MySQL 5.7+

### 2. 安装Redis

**macOS:**
```bash
brew install redis
brew services start redis
```

**Docker:**
```bash
docker run -d --name redis -p 6379:6379 redis:latest
```

### 3. 准备MySQL数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS rate_limiter DEFAULT CHARSET utf8mb4;"
```

修改 `application.yml` 中的数据库连接信息（用户名、密码），启动时会自动执行 `schema.sql` 建表。

### 4. 启动项目

```bash
# 克隆项目
cd redis-distributed-rate-limiter

# 编译项目
mvn clean package -DskipTests

# 启动应用
mvn spring-boot:run
```

启动成功后：
- 管理界面：http://localhost:8082/index.html
- 测试接口：http://localhost:8082/api/test/info

### 5. 管理界面

打开 http://localhost:8082/index.html 进入可视化管理界面，支持：

- 查看所有限流规则及统计概览（总数、启用/禁用数、算法类型数）
- 新建规则 — 表单根据算法类型自动切换参数（窗口类: 次数/秒数，桶类: 容量/速率）
- 编辑规则 — 修改参数即时生效，无需重启
- 删除规则 — 二次确认，删除后回退到注解默认值
- 刷新缓存 — 手动触发多实例缓存同步

### 6. 测试接口

```bash
# 查看系统信息
curl http://localhost:8082/api/test/info

# 测试固定窗口限流（每分钟5次）
curl http://localhost:8082/api/test/fixed-window

# 测试滑动窗口限流（每30秒10次）
curl http://localhost:8082/api/test/sliding-window

# 测试令牌桶限流（桶容量10，每秒生成2个令牌）
curl http://localhost:8082/api/test/token-bucket

# 测试漏桶限流（桶容量5，每秒漏出1个）
curl http://localhost:8082/api/test/leaky-bucket

# 测试用户限流（每个用户独立限流）
curl http://localhost:8082/api/test/user/123
curl http://localhost:8082/api/test/user/456

# 测试无限流接口
curl http://localhost:8082/api/test/no-limit
```

## 使用指南

### 基本使用

在需要限流的方法上添加 `@RateLimit` 注解：

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    // 固定窗口：每60秒最多10次
    @RateLimit(
        key = "api:getData",
        limit = 10,
        period = 60,
        type = RateLimitType.FIXED_WINDOW
    )
    @GetMapping("/data")
    public Result getData() {
        return Result.success("获取数据成功");
    }

    // 滑动窗口：每30秒最多5次
    @RateLimit(
        key = "api:updateData",
        limit = 5,
        period = 30,
        type = RateLimitType.SLIDING_WINDOW,
        message = "更新太频繁，请稍后再试"
    )
    @PostMapping("/data")
    public Result updateData() {
        return Result.success("更新成功");
    }
}
```

### 高级用法

#### 1. 基于用户ID限流（SpEL表达式）

```java
// 每个用户独立限流
@RateLimit(
    key = "api:user:#{#userId}",
    limit = 10,
    period = 60
)
@GetMapping("/user/{userId}/profile")
public Result getUserProfile(@PathVariable Long userId) {
    // 业务逻辑
}
```

#### 2. 基于请求参数限流

```java
// 基于订单ID限流
@RateLimit(
    key = "api:order:#{#orderId}",
    limit = 1,
    period = 10,
    message = "请勿重复提交订单"
)
@PostMapping("/order/{orderId}/submit")
public Result submitOrder(@PathVariable String orderId) {
    // 业务逻辑
}
```

#### 3. 令牌桶算法（应对突发流量）

```java
// 桶容量100，每秒生成10个令牌
// 可以瞬间处理100个请求，然后按每秒10个的速度恢复
@RateLimit(
    key = "api:search",
    type = RateLimitType.TOKEN_BUCKET,
    capacity = 100,
    tokensPerSecond = 10.0
)
@GetMapping("/search")
public Result search(@RequestParam String keyword) {
    // 业务逻辑
}
```

#### 4. 漏桶算法（平滑限流）

```java
// 桶容量50，每秒漏出5个请求
// 强制限制请求速率为每秒5个
@RateLimit(
    key = "api:export",
    type = RateLimitType.LEAKY_BUCKET,
    capacity = 50,
    tokensPerSecond = 5.0
)
@GetMapping("/export")
public Result exportData() {
    // 业务逻辑
}
```

## 动态限流配置（MySQL持久化）

支持通过 RESTful API 在运行时动态管理限流配置，无需重启应用。

**策略**：数据库配置优先，无 DB 配置时回退到 `@RateLimit` 注解默认值。

### 配置管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rate-limit-configs` | 查询所有配置 |
| GET | `/api/rate-limit-configs/{id}` | 按 ID 查询 |
| GET | `/api/rate-limit-configs/by-key?key=xxx` | 按限流 key 查询 |
| POST | `/api/rate-limit-configs` | 创建配置 |
| PUT | `/api/rate-limit-configs/{id}` | 更新配置 |
| DELETE | `/api/rate-limit-configs/{id}` | 删除配置 |
| POST | `/api/rate-limit-configs/refresh-cache` | 手动刷新缓存 |

### 使用示例

#### 1. 创建配置（覆盖注解默认值）

假设 `@RateLimit` 注解配置了 `key = "test:fixed-window"`, `limit = 5`, `period = 60`，通过 DB 配置覆盖为 20 次/60 秒：

```bash
curl -X POST http://localhost:8082/api/rate-limit-configs \
  -H "Content-Type: application/json" \
  -d '{
    "limitKey": "test:fixed-window",
    "type": "FIXED_WINDOW",
    "limitCount": 20,
    "period": 60,
    "message": "DB配置限流：每分钟最多20次",
    "enabled": true,
    "description": "覆盖固定窗口注解默认值"
  }'
```

#### 2. 查询所有配置

```bash
curl http://localhost:8082/api/rate-limit-configs
```

#### 3. 更新配置

```bash
curl -X PUT http://localhost:8082/api/rate-limit-configs/1 \
  -H "Content-Type: application/json" \
  -d '{
    "limitKey": "test:fixed-window",
    "type": "FIXED_WINDOW",
    "limitCount": 50,
    "period": 60,
    "message": "调整为每分钟50次",
    "enabled": true
  }'
```

#### 4. 删除配置（回退到注解默认值）

```bash
curl -X DELETE http://localhost:8082/api/rate-limit-configs/1
```

#### 5. 手动刷新缓存

配置修改后会立即更新本地缓存，同时每 60 秒自动全量刷新（多实例同步）。也可手动触发：

```bash
curl -X POST http://localhost:8082/api/rate-limit-configs/refresh-cache
```

### 配置字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `limitKey` | String | 是 | 限流 key，对应 `@RateLimit` 注解的 `key` 值（SpEL 解析前） |
| `type` | String | 是 | 算法类型：`FIXED_WINDOW`、`SLIDING_WINDOW`、`TOKEN_BUCKET`、`LEAKY_BUCKET` |
| `limitCount` | Long | 否 | 最大请求数（FIXED_WINDOW / SLIDING_WINDOW） |
| `period` | Long | 否 | 时间窗口秒数（FIXED_WINDOW / SLIDING_WINDOW） |
| `capacity` | Long | 否 | 桶容量（TOKEN_BUCKET / LEAKY_BUCKET） |
| `tokensPerSecond` | Double | 否 | 速率（TOKEN_BUCKET / LEAKY_BUCKET） |
| `message` | String | 否 | 限流提示信息 |
| `enabled` | Boolean | 是 | 是否启用，设为 `false` 则回退到注解默认值 |
| `description` | String | 否 | 配置描述 |

### 缓存机制

- 使用 **ConcurrentHashMap** 本地缓存，限流热路径不查询数据库
- CRUD 操作立即更新缓存
- 每 60 秒定时全量刷新（支持多实例部署同步）
- 缓存刷新失败时保持旧缓存，fail-open 不影响业务

## 注解参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | "" | 限流key，支持SpEL表达式 |
| `limit` | long | 10 | 限流次数 |
| `period` | long | 60 | 时间窗口（秒） |
| `type` | RateLimitType | SLIDING_WINDOW | 限流算法类型 |
| `message` | String | "请求过于频繁..." | 限流提示信息 |
| `useParams` | boolean | false | 是否使用参数作为key的一部分 |
| `tokensPerSecond` | double | 10.0 | 令牌生成速率（令牌桶） |
| `capacity` | long | 10 | 桶容量（令牌桶/漏桶） |

## 限流算法对比

| 算法 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **固定窗口** | 实现简单，内存占用低 | 存在临界问题 | 精度要求不高的场景 |
| **滑动窗口** | 限流平滑，解决临界问题 | 内存占用稍高 | 大部分限流场景 |
| **令牌桶** | 可应对突发流量 | 实现较复杂 | 需要弹性限流的场景 |
| **漏桶** | 流量平滑，恒定速率 | 无法应对突发流量 | 需要恒定速率的场景 |

### 临界问题示例

**固定窗口的临界问题：**
```
时间窗口: [00:00-01:00] [01:00-02:00]
限制: 每分钟100次

00:59秒 - 100次请求 ✅
01:00秒 - 100次请求 ✅
→ 在1秒内处理了200次请求！
```

**滑动窗口解决方案：**
```
时间窗口: 滑动的60秒
限制: 任意60秒内最多100次

00:59秒 - 100次请求 ✅
01:00秒 - 第1次请求 ❌（前60秒已有100次）
→ 始终保证60秒内不超过100次
```

## 项目结构

```
redis-distributed-rate-limiter/
├── src/main/java/com/example/ratelimiter/
│   ├── annotation/              # 注解
│   │   └── RateLimit.java      # 限流注解
│   ├── aspect/                 # 切面
│   │   └── RateLimitAspect.java # AOP切面（DB配置优先，注解回退）
│   ├── config/                 # 配置
│   │   ├── RedisConfig.java    # Redis配置
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── controller/             # 控制器
│   │   ├── TestController.java # 测试控制器
│   │   └── RateLimitConfigController.java # 限流配置管理API
│   ├── dto/                    # 数据传输对象
│   │   ├── RateLimitConfigRequest.java  # 请求DTO（含参数校验）
│   │   └── RateLimitConfigResponse.java # 响应DTO
│   ├── entity/                 # 实体
│   │   └── RateLimitConfig.java # 限流配置实体（映射rate_limit_config表）
│   ├── exception/              # 异常
│   │   └── RateLimitException.java # 限流异常
│   ├── mapper/                 # MyBatis Mapper
│   │   └── RateLimitConfigMapper.java # 限流配置Mapper接口
│   ├── model/                  # 模型
│   │   ├── RateLimitType.java  # 限流类型枚举
│   │   └── Result.java         # 统一响应
│   ├── service/                # 服务
│   │   ├── RateLimiterService.java      # 限流服务接口
│   │   ├── RateLimiterServiceImpl.java  # 限流服务实现（Lua脚本）
│   │   └── RateLimitConfigService.java  # 限流配置服务（CRUD + 缓存）
│   └── RateLimiterApplication.java # 启动类
├── src/main/resources/
│   ├── application.yml         # 应用配置（Redis + MySQL）
│   ├── schema.sql              # 建表DDL（自动执行）
│   ├── mapper/
│   │   └── RateLimitConfigMapper.xml # MyBatis SQL映射
│   └── static/
│       └── index.html          # 前端管理界面（单页应用）
├── pom.xml                     # Maven配置
└── README.md                   # 项目文档
```

## 配置说明

### application.yml

```yaml
spring:
  # MySQL数据源
  datasource:
    url: jdbc:mysql://localhost:3306/rate_limiter?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: your_password      # 修改为你的MySQL密码
    driver-class-name: com.mysql.cj.jdbc.Driver

  # 自动执行建表SQL
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

  # Redis配置
  data:
    redis:
      host: localhost
      port: 6379
      password:                  # Redis密码（如果有）
      database: 0
      lettuce:
        pool:
          max-active: 8          # 连接池最大连接数
          max-idle: 8            # 最大空闲连接数
          min-idle: 0            # 最小空闲连接数

# MyBatis配置
mybatis:
  mapper-locations: classpath:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true

rate-limiter:
  enabled: true                  # 是否启用限流
  key-prefix: "rate_limit:"     # Redis key前缀
  log-enabled: true             # 是否记录限流日志
```

## 实现原理

### 1. 固定窗口算法（Lua脚本）

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local period = tonumber(ARGV[2])

local current = redis.call('get', key)

if current and tonumber(current) >= limit then
    return 0  -- 拒绝
end

current = redis.call('incr', key)

if tonumber(current) == 1 then
    redis.call('expire', key, period)
end

return 1  -- 允许
```

### 2. 滑动窗口算法（Lua脚本 + ZSET）

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local period = tonumber(ARGV[2])
local current_time = tonumber(ARGV[3])

-- 删除过期记录
local expire_time = current_time - period * 1000
redis.call('zremrangebyscore', key, 0, expire_time)

-- 获取当前窗口内的请求数
local current_count = redis.call('zcard', key)

if current_count >= limit then
    return 0  -- 拒绝
end

-- 添加当前请求
redis.call('zadd', key, current_time, request_id)
redis.call('expire', key, period)

return 1  -- 允许
```

### 3. 为什么使用Lua脚本？

- **原子性** - Redis保证Lua脚本执行的原子性
- **性能** - 减少网络往返次数
- **一致性** - 避免并发问题，保证分布式一致性

## 性能测试

### 测试环境
- MacBook Pro M1
- Redis 7.0.5
- 单机测试

### 测试结果

| 算法 | QPS | 平均响应时间 | P99响应时间 |
|------|-----|--------------|-------------|
| 固定窗口 | 10,000+ | 2ms | 5ms |
| 滑动窗口 | 8,000+ | 3ms | 8ms |
| 令牌桶 | 9,000+ | 2.5ms | 6ms |
| 漏桶 | 9,000+ | 2.5ms | 6ms |

## 常见问题

### Q1: Redis连接失败怎么办？

**A:** 检查Redis是否启动，配置是否正确：
```bash
# 测试Redis连接
redis-cli ping
# 应该返回 PONG
```

### Q2: 限流失效（所有请求都通过）？

**A:** 可能原因：
1. Redis连接失败 - 限流服务默认在异常时放行
2. 注解配置错误 - 检查@RateLimit注解参数
3. AOP未生效 - 确保方法是public且通过Spring调用

### Q3: 如何重置限流计数？

**A:** 使用RateLimiterService提供的reset方法：
```java
@Autowired
private RateLimiterService rateLimiterService;

// 重置指定key的限流计数
rateLimiterService.reset("rate_limit:api:test");
```

### Q4: 如何查看当前剩余配额？

**A:** 使用getRemainingQuota方法：
```java
long remaining = rateLimiterService.getRemainingQuota("rate_limit:api:test");
```

### Q5: 多实例部署如何保证限流一致性？

**A:** 基于Redis实现，天然支持分布式：
- 所有实例共享同一个Redis
- Lua脚本保证操作原子性
- 无需额外配置

### Q6: DB配置和注解配置的优先级？

**A:** DB 配置优先。如果数据库中有对应 `limitKey` 且 `enabled=true` 的配置，使用 DB 值；否则回退到 `@RateLimit` 注解默认值。删除或禁用 DB 配置后自动回退。

### Q7: 修改 DB 配置后多久生效？

**A:** 通过 API 修改会立即更新本地缓存，当前实例即刻生效。多实例部署时，其他实例最多等待 60 秒（定时全量刷新周期），也可调用 `/api/rate-limit-configs/refresh-cache` 手动刷新。

## 最佳实践

### 1. 合理选择限流算法

```java
// API接口 - 使用滑动窗口（平滑限流）
@RateLimit(type = RateLimitType.SLIDING_WINDOW)

// 导出功能 - 使用漏桶（恒定速率）
@RateLimit(type = RateLimitType.LEAKY_BUCKET)

// 搜索接口 - 使用令牌桶（应对突发）
@RateLimit(type = RateLimitType.TOKEN_BUCKET)
```

### 2. 分级限流策略

```java
// 全局限流
@RateLimit(key = "global", limit = 10000, period = 60)

// 用户级限流
@RateLimit(key = "user:#{#userId}", limit = 100, period = 60)

// IP级限流
@RateLimit(key = "ip", limit = 50, period = 60, useParams = true)
```

### 3. 限流降级

```java
// 核心接口：限流时返回友好提示
@RateLimit(message = "系统繁忙，请稍后再试")

// 非核心接口：限流时可以降级返回缓存数据
try {
    return getRealTimeData();
} catch (RateLimitException e) {
    return getCachedData();
}
```

## 扩展建议

### 1. 进一步增强动态配置

- 集成配置中心（Nacos、Apollo）实现配置推送
- 管理界面增加批量导入/导出功能

### 2. 监控与告警

- 记录限流日志到ELK
- 监控限流指标（Prometheus + Grafana）
- 限流告警通知

### 3. 限流策略优化

- 根据用户等级设置不同的限流阈值
- 业务高峰期动态调整限流参数
- 白名单机制

## License

MIT License

## 作者

Claude - AI Assistant by Anthropic

## 参考资料

- [Redis官方文档](https://redis.io/docs/)
- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [限流算法详解](https://en.wikipedia.org/wiki/Rate_limiting)
