package com.vedha.urlshortener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Real-time counters kept in Redis, separate from the durable click_event table.
 * These back the "live" numbers on the dashboard while the Kafka -> MySQL pipeline
 * builds the detailed historical breakdowns (see AnalyticsService for how the two
 * are reconciled).
 *
 * All operations degrade gracefully if Redis is unavailable: increments are
 * best-effort (a missed increment is corrected at the next reconciliation from
 * MySQL - see AnalyticsService.reconcileCounters), and getTotal signals
 * "unavailable" via a sentinel rather than silently returning 0, so callers don't
 * mistake "Redis is down" for "this URL truly has zero clicks".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickCounterService {

    public static final long UNAVAILABLE = -1L;

    private static final String TOTAL_CLICKS_PREFIX = "clicks:total:";
    private static final String TOP_URLS_ZSET = "clicks:leaderboard";

    private final RedisTemplate<String, Object> redisTemplate;

    public void increment(String shortCode) {
        try {
            redisTemplate.opsForValue().increment(TOTAL_CLICKS_PREFIX + shortCode);
            redisTemplate.opsForZSet().incrementScore(TOP_URLS_ZSET, shortCode, 1);
        } catch (Exception ex) {
            log.warn("Redis increment failed for shortCode={}; MySQL (via Kafka) remains the " +
                    "source of truth so no click is lost, only the live counter lags: {}",
                    shortCode, ex.getMessage());
        }
    }

    /** Returns {@link #UNAVAILABLE} if Redis can't be reached - never a false zero. */
    public long getTotal(String shortCode) {
        try {
            Object value = redisTemplate.opsForValue().get(TOTAL_CLICKS_PREFIX + shortCode);
            return value != null ? Long.parseLong(value.toString()) : 0L;
        } catch (Exception ex) {
            log.warn("Redis read failed for shortCode={} total clicks: {}", shortCode, ex.getMessage());
            return UNAVAILABLE;
        }
    }

    public void seedTotal(String shortCode, long value) {
        try {
            redisTemplate.opsForValue().set(TOTAL_CLICKS_PREFIX + shortCode, String.valueOf(value));
        } catch (Exception ex) {
            log.warn("Redis seed failed for shortCode={}: {}", shortCode, ex.getMessage());
        }
    }
}
