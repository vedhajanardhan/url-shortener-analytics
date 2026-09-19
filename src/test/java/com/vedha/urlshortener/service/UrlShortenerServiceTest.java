package com.vedha.urlshortener.service;

import com.vedha.urlshortener.dto.ShortenRequest;
import com.vedha.urlshortener.dto.UpdateUrlRequest;
import com.vedha.urlshortener.entity.Role;
import com.vedha.urlshortener.entity.UrlMapping;
import com.vedha.urlshortener.entity.User;
import com.vedha.urlshortener.exception.AliasAlreadyTakenException;
import com.vedha.urlshortener.exception.NotOwnerException;
import com.vedha.urlshortener.exception.ShortCodeNotFoundException;
import com.vedha.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests around the shortening/ownership logic - mocks the repository/
 * cache/kafka/validation layers so this runs fast with no Docker/Testcontainers
 * dependency, unlike the full integration suite for the redirect + Kafka
 * consumer + auth flow (see AuthAndUrlFlowIntegrationTest).
 */
@ExtendWith(MockitoExtension.class)
class UrlShortenerServiceTest {

    @Mock private UrlMappingRepository urlMappingRepository;
    @Mock private UrlCacheService urlCacheService;
    @Mock private ClickCounterService clickCounterService;
    @Mock private ClickEventProducer clickEventProducer;
    @Mock private UrlValidationService urlValidationService;

    @InjectMocks
    private UrlShortenerService urlShortenerService;

    private User owner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlShortenerService, "baseUrl", "http://localhost:8080");
        owner = User.builder().id(1L).username("vedha").role(Role.USER).enabled(true).build();
    }

    @Test
    void shorten_withCustomAlias_rejectsDuplicate() {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://example.com/some/long/path");
        request.setCustomAlias("mycustom");

        when(urlMappingRepository.existsByShortCode("mycustom")).thenReturn(true);

        assertThrows(AliasAlreadyTakenException.class, () -> urlShortenerService.shorten(request, owner));
        verify(urlValidationService).validate(request.getLongUrl());
    }

    @Test
    void shorten_assignsOwnerToNewMapping() {
        ShortenRequest request = new ShortenRequest();
        request.setLongUrl("https://example.com/path");

        when(urlMappingRepository.save(any(UrlMapping.class)))
                .thenAnswer(inv -> {
                    UrlMapping m = inv.getArgument(0);
                    m.setId(42L);
                    return m;
                });

        var response = urlShortenerService.shorten(request, owner);

        assertNotNull(response.getShortCode());
        verify(urlMappingRepository, atLeastOnce()).save(argThat(m -> m.getOwner() == owner));
    }

    @Test
    void resolveAndTrack_cacheHit_skipsRepository() {
        when(urlCacheService.get("abc123")).thenReturn("https://example.com");

        String result = urlShortenerService.resolveAndTrack("abc123", "1.2.3.4", "Mozilla/5.0", null);

        assertEquals("https://example.com", result);
        verify(urlMappingRepository, never()).findByShortCodeAndActiveTrue(anyString());
    }

    @Test
    void resolveAndTrack_cacheMiss_fallsBackToRepository() {
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc123")
                .longUrl("https://example.com")
                .active(true)
                .build();

        when(urlCacheService.get("abc123")).thenReturn(null);
        when(urlMappingRepository.findByShortCodeAndActiveTrue("abc123")).thenReturn(Optional.of(mapping));

        String result = urlShortenerService.resolveAndTrack("abc123", "1.2.3.4", "Mozilla/5.0", null);

        assertEquals("https://example.com", result);
        verify(urlCacheService).put(eq("abc123"), eq("https://example.com"), any());
    }

    @Test
    void resolveAndTrack_unknownShortCode_throws() {
        when(urlCacheService.get("missing")).thenReturn(null);
        when(urlMappingRepository.findByShortCodeAndActiveTrue("missing")).thenReturn(Optional.empty());

        assertThrows(ShortCodeNotFoundException.class,
                () -> urlShortenerService.resolveAndTrack("missing", "1.2.3.4", "UA", null));
    }

    @Test
    void update_byNonOwner_isRejected() {
        User otherUser = User.builder().id(2L).username("someoneElse").role(Role.USER).enabled(true).build();
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc123").longUrl("https://example.com").owner(owner).active(true).build();

        when(urlMappingRepository.findByShortCodeAndActiveTrue("abc123")).thenReturn(Optional.of(mapping));

        UpdateUrlRequest request = new UpdateUrlRequest();
        request.setLongUrl("https://new-url.com");

        assertThrows(NotOwnerException.class, () -> urlShortenerService.update("abc123", request, otherUser));
    }

    @Test
    void update_byOwner_succeedsAndEvictsCache() {
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc123").longUrl("https://example.com").owner(owner).active(true).build();

        when(urlMappingRepository.findByShortCodeAndActiveTrue("abc123")).thenReturn(Optional.of(mapping));

        UpdateUrlRequest request = new UpdateUrlRequest();
        request.setLongUrl("https://new-url.com");

        var response = urlShortenerService.update("abc123", request, owner);

        assertEquals("https://new-url.com", response.getLongUrl());
        verify(urlCacheService).evict("abc123");
    }

    @Test
    void delete_byOwner_deactivatesAndEvictsCache() {
        UrlMapping mapping = UrlMapping.builder()
                .shortCode("abc123").longUrl("https://example.com").owner(owner).active(true).build();

        when(urlMappingRepository.findByShortCodeAndActiveTrue("abc123")).thenReturn(Optional.of(mapping));

        urlShortenerService.delete("abc123", owner);

        assertFalse(mapping.isActive());
        verify(urlCacheService).evict("abc123");
    }
}
