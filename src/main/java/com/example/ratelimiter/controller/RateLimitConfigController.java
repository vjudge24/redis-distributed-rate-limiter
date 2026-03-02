package com.example.ratelimiter.controller;

import com.example.ratelimiter.dto.RateLimitConfigRequest;
import com.example.ratelimiter.dto.RateLimitConfigResponse;
import com.example.ratelimiter.entity.RateLimitConfig;
import com.example.ratelimiter.model.Result;
import com.example.ratelimiter.service.RateLimitConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rate-limit-configs")
public class RateLimitConfigController {

    private final RateLimitConfigService configService;

    public RateLimitConfigController(RateLimitConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public Result<List<RateLimitConfigResponse>> findAll() {
        List<RateLimitConfigResponse> list = configService.findAll().stream()
                .map(RateLimitConfigResponse::fromEntity)
                .toList();
        return Result.success("查询成功", list);
    }

    @GetMapping("/{id}")
    public Result<RateLimitConfigResponse> findById(@PathVariable Long id) {
        RateLimitConfig config = configService.findById(id);
        if (config == null) {
            return Result.error(404, "限流配置不存在: id=" + id);
        }
        return Result.success("查询成功", RateLimitConfigResponse.fromEntity(config));
    }

    @GetMapping("/by-key")
    public Result<RateLimitConfigResponse> findByKey(@RequestParam String key) {
        RateLimitConfig config = configService.findByLimitKey(key);
        if (config == null) {
            return Result.error(404, "限流配置不存在: key=" + key);
        }
        return Result.success("查询成功", RateLimitConfigResponse.fromEntity(config));
    }

    @PostMapping
    public Result<RateLimitConfigResponse> create(@Valid @RequestBody RateLimitConfigRequest request) {
        RateLimitConfig config = toEntity(request);
        RateLimitConfig created = configService.create(config);
        return Result.success("创建成功", RateLimitConfigResponse.fromEntity(created));
    }

    @PutMapping("/{id}")
    public Result<RateLimitConfigResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody RateLimitConfigRequest request) {
        RateLimitConfig config = toEntity(request);
        RateLimitConfig updated = configService.update(id, config);
        return Result.success("更新成功", RateLimitConfigResponse.fromEntity(updated));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        configService.deleteById(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        configService.refreshCache();
        return Result.success("缓存刷新成功", null);
    }

    private RateLimitConfig toEntity(RateLimitConfigRequest request) {
        RateLimitConfig config = new RateLimitConfig();
        config.setLimitKey(request.getLimitKey());
        config.setType(request.getType());
        config.setLimitCount(request.getLimitCount());
        config.setPeriod(request.getPeriod());
        config.setCapacity(request.getCapacity());
        config.setTokensPerSecond(request.getTokensPerSecond());
        config.setMessage(request.getMessage());
        config.setEnabled(request.getEnabled());
        config.setDescription(request.getDescription());
        return config;
    }
}
