package com.vedha.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Cache-aside layer in front of MySQL for shortCode -> longUrl lookups.
 * This is what makes the redirect endpoint fast: on a cache hit we never touch the DB.
 *
 * Every Redis call is wrapped so a Redis outage degrades to "always hit MySQL"
 * instead of taking the whole redirect path down with it - Redis here is a
 * pure performance optimization, never a hard dependency for correctness.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlCacheService {

    private static final String CACHE_PREFIX = "url:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.cache.url-mapping-ttl-hours}")
    private long ttlHours;

    public String get(String shortCode) {
        try {
            Object value = redisTemplate.opsForValue().get(CACHE_PREFIX + shortCode);
            return value != null ? value.toString() : null;
        } catch (Exception ex) {
            log.warn("Redis GET failed for shortCode={}, falling back to MySQL: {}", shortCode, ex.getMessage());
            return null; // treat as a cache miss - caller reads from the DB
        }
    }

    public void put(String shortCode, String longUrl) {
        put(shortCode, longUrl, null);
    }

    /**
     * @param expiresAt if set, the cache TTL is capped so a cached entry never
     *                  outlives the URL's own expiry - otherwise an expired link
     *                  could keep resolving from cache for up to the full TTL
     *                  after it should have stopped working.
     */
    public void put(String shortCode, String longUrl, java.time.LocalDateTime expiresAt) {
        Duration ttl = Duration.ofHours(ttlHours);
        if (expiresAt != null) {
            Duration untilExpiry = Duration.between(java.time.LocalDateTime.now(), expiresAt);
            if (untilExpiry.isNegative() || untilExpiry.isZero()) {
                return; // already expired - don't cache it at all
            }
            if (untilExpiry.compareTo(ttl) < 0) {
                ttl = untilExpiry;
            }
        }
        try {
            redisTemplate.opsForValue().set(CACHE_PREFIX + shortCode, longUrl, ttl);
        } catch (Exception ex) {
            log.warn("Redis SET failed for shortCode={}, continuing without cache: {}", shortCode, ex.getMessage());
        }
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(CACHE_PREFIX + shortCode);
        } catch (Exception ex) {
            log.warn("Redis DEL failed for shortCode={}: {}", shortCode, ex.getMessage());
        }
    }
}
