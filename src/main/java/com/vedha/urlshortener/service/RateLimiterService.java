package com.vedha.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Fixed-window rate limiter for the /shorten endpoint, keyed by client IP.
 * Redis INCR + EXPIRE gives an atomic, distributed counter cheaply.
 *
 * Fails OPEN: if Redis is unreachable we allow the request rather than block
 * all traffic. Rate limiting is a defense against abuse, not a correctness
 * requirement - losing it temporarily during a Redis outage is a much smaller
 * problem than an outage taking down the entire create-URL endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.rate-limit.shorten-requests-per-minute}")
    private int limitPerMinute;

    public boolean allow(String clientKey) {
        String key = "ratelimit:shorten:" + clientKey;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }
            return count == null || count <= limitPerMinute;
        } catch (Exception ex) {
            log.warn("Redis unavailable for rate limiting, allowing request (failing open): {}", ex.getMessage());
            return true;
        }
    }
}
