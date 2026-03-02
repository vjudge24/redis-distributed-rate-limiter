CREATE TABLE IF NOT EXISTS rate_limit_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    limit_key         VARCHAR(255) NOT NULL COMMENT '限流key（@RateLimit注解的key，SpEL解析前）',
    type              VARCHAR(30)  NOT NULL COMMENT '算法类型：FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET',
    limit_count       BIGINT       NULL     COMMENT '最大请求数（FIXED_WINDOW/SLIDING_WINDOW）',
    period            BIGINT       NULL     COMMENT '时间窗口秒数（FIXED_WINDOW/SLIDING_WINDOW）',
    capacity          BIGINT       NULL     COMMENT '桶容量（TOKEN_BUCKET/LEAKY_BUCKET）',
    tokens_per_second DOUBLE       NULL     COMMENT '速率（TOKEN_BUCKET/LEAKY_BUCKET）',
    message           VARCHAR(500) NULL     COMMENT '限流提示信息',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    description       VARCHAR(500) NULL     COMMENT '描述',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_limit_key (limit_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
