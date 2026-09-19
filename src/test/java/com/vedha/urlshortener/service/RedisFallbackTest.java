package com.vedha.urlshortener.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the Redis-outage fallback actually works, using a REAL RedisTemplate
 * pointed at a port nothing is listening on - not a mock - so this exercises the
 * exact exception path a real Redis outage would hit (connection refused),
 * rather than a Mockito stub standing in for "Redis is down".
 */
class RedisFallbackTest {

    private RedisTemplate<String, Object> unreachableRedisTemplate;
    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        // Port 1 is a reserved/privileged port nothing will ever be listening on
        // in a test environment, giving a fast, reliable "connection refused".
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("localhost", 1);
        connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();

        unreachableRedisTemplate = new RedisTemplate<>();
        unreachableRedisTemplate.setConnectionFactory(connectionFactory);
        unreachableRedisTemplate.setKeySerializer(new StringRedisSerializer());
        unreachableRedisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        unreachableRedisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void urlCacheService_get_returnsNullInsteadOfThrowing() {
        UrlCacheService cacheService = new UrlCacheService(unreachableRedisTemplate);
        ReflectionTestUtils.setField(cacheService, "ttlHours", 24L);

        assertDoesNotThrow(() -> {
            String result = cacheService.get("anyCode");
            assertNull(result); // treated as a cache miss, not a crash
        });
    }

    @Test
    void urlCacheService_put_doesNotThrow() {
        UrlCacheService cacheService = new UrlCacheService(unreachableRedisTemplate);
        ReflectionTestUtils.setField(cacheService, "ttlHours", 24L);

        assertDoesNotThrow(() -> cacheService.put("anyCode", "https://example.com"));
    }

    @Test
    void clickCounterService_getTotal_returnsUnavailableSentinel() {
        ClickCounterService counterService = new ClickCounterService(unreachableRedisTemplate);

        long result = counterService.getTotal("anyCode");

        assertEquals(ClickCounterService.UNAVAILABLE, result,
                "Must signal 'unavailable', never a false zero that looks like a real click count of 0");
    }

    @Test
    void clickCounterService_increment_doesNotThrow() {
        ClickCounterService counterService = new ClickCounterService(unreachableRedisTemplate);
        assertDoesNotThrow(() -> counterService.increment("anyCode"));
    }

    @Test
    void rateLimiterService_failsOpenWhenRedisIsDown() {
        RateLimiterService rateLimiterService = new RateLimiterService(unreachableRedisTemplate);
        ReflectionTestUtils.setField(rateLimiterService, "limitPerMinute", 1);

        // Even called many times in a row, every call must be allowed since
        // Redis (the only thing that could say "no") is unreachable.
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiterService.allow("1.2.3.4"), "Rate limiter must fail open, not block traffic");
        }
    }

    @Test
    void qrCodeService_stillGeneratesWhenCacheIsDown() throws Exception {
        QrCodeService qrCodeService = new QrCodeService(unreachableRedisTemplate);
        byte[] png = qrCodeService.generatePng("https://example.com", "anyCode");

        assertNotNull(png);
        assertTrue(png.length > 0);
    }
}
