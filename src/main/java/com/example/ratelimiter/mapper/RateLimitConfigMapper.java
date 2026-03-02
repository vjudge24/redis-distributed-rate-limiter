package com.example.ratelimiter.mapper;

import com.example.ratelimiter.entity.RateLimitConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RateLimitConfigMapper {

    List<RateLimitConfig> findAll();

    RateLimitConfig findById(@Param("id") Long id);

    RateLimitConfig findByLimitKey(@Param("limitKey") String limitKey);

    int insert(RateLimitConfig config);

    int update(RateLimitConfig config);

    int deleteById(@Param("id") Long id);

    int existsByLimitKey(@Param("limitKey") String limitKey);
}
