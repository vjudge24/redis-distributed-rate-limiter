package com.example.ratelimiter.dto;

import com.example.ratelimiter.entity.RateLimitConfig;

import java.time.LocalDateTime;

public class RateLimitConfigResponse {

    private Long id;
    private String limitKey;
    private String type;
    private Long limitCount;
    private Long period;
    private Long capacity;
    private Double tokensPerSecond;
    private String message;
    private Boolean enabled;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RateLimitConfigResponse fromEntity(RateLimitConfig config) {
        RateLimitConfigResponse response = new RateLimitConfigResponse();
        response.setId(config.getId());
        response.setLimitKey(config.getLimitKey());
        response.setType(config.getType());
        response.setLimitCount(config.getLimitCount());
        response.setPeriod(config.getPeriod());
        response.setCapacity(config.getCapacity());
        response.setTokensPerSecond(config.getTokensPerSecond());
        response.setMessage(config.getMessage());
        response.setEnabled(config.getEnabled());
        response.setDescription(config.getDescription());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
