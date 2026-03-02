package com.example.ratelimiter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class RateLimitConfigRequest {

    @NotBlank(message = "限流key不能为空")
    private String limitKey;

    @NotBlank(message = "算法类型不能为空")
    private String type;

    @Positive(message = "最大请求数必须为正数")
    private Long limitCount;

    @Positive(message = "时间窗口必须为正数")
    private Long period;

    @Positive(message = "桶容量必须为正数")
    private Long capacity;

    @Positive(message = "速率必须为正数")
    private Double tokensPerSecond;

    private String message;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled = true;

    private String description;

    public String getLimitKey() {
        return limitKey;
    }

    public void setLimitKey(String limitKey) {
        this.limitKey = limitKey;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getLimitCount() {
        return limitCount;
    }

    public void setLimitCount(Long limitCount) {
        this.limitCount = limitCount;
    }

    public Long getPeriod() {
        return period;
    }

    public void setPeriod(Long period) {
        this.period = period;
    }

    public Long getCapacity() {
        return capacity;
    }

    public void setCapacity(Long capacity) {
        this.capacity = capacity;
    }

    public Double getTokensPerSecond() {
        return tokensPerSecond;
    }

    public void setTokensPerSecond(Double tokensPerSecond) {
        this.tokensPerSecond = tokensPerSecond;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
