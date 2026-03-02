package com.example.ratelimiter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 分布式限流系统启动类
 *
 * 技术栈：
 * - JDK 17
 * - Spring Boot 3.2.1
 * - Redis (Lettuce)
 * - AOP
 *
 * 限流算法：
 * - 固定窗口计数器
 * - 滑动窗口计数器
 * - 令牌桶算法
 * - 漏桶算法
 *
 * @author Claude
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.example.ratelimiter.mapper")
public class RateLimiterApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateLimiterApplication.class, args);
        System.out.println("""

                ╔═══════════════════════════════════════════════════════════╗
                ║   Redis Distributed Rate Limiter 已成功启动              ║
                ║   ------------------------------------------------         ║
                ║   技术栈: JDK 17 + Spring Boot 3 + Redis                 ║
                ║   端口: 8082                                              ║
                ║   限流算法: 固定窗口/滑动窗口/令牌桶/漏桶                ║
                ║   ------------------------------------------------         ║
                ║   测试接口:                                               ║
                ║   - GET  http://localhost:8082/api/test/fixed-window      ║
                ║   - GET  http://localhost:8082/api/test/sliding-window    ║
                ║   - GET  http://localhost:8082/api/test/token-bucket      ║
                ║   - GET  http://localhost:8082/api/test/leaky-bucket      ║
                ║   - GET  http://localhost:8082/api/test/user/{userId}     ║
                ╚═══════════════════════════════════════════════════════════╝
                """);
    }
}
