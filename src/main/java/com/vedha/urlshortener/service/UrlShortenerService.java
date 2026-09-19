package com.vedha.urlshortener.service;

import com.vedha.urlshortener.dto.ClickEventMessage;
import com.vedha.urlshortener.dto.ShortenRequest;
import com.vedha.urlshortener.dto.ShortenResponse;
import com.vedha.urlshortener.dto.UpdateUrlRequest;
import com.vedha.urlshortener.entity.UrlMapping;
import com.vedha.urlshortener.entity.User;
import com.vedha.urlshortener.exception.AliasAlreadyTakenException;
import com.vedha.urlshortener.exception.NotOwnerException;
import com.vedha.urlshortener.exception.ShortCodeNotFoundException;
import com.vedha.urlshortener.repository.UrlMappingRepository;
import com.vedha.urlshortener.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final UrlMappingRepository urlMappingRepository;
    private final UrlCacheService urlCacheService;
    private final ClickCounterService clickCounterService;
    private final ClickEventProducer clickEventProducer;
    private final UrlValidationService urlValidationService;

    @Value("${app.base-url}")
    private String baseUrl;

    @Transactional
    public ShortenResponse shorten(ShortenRequest request, User owner) {
        urlValidationService.validate(request.getLongUrl());

        String shortCode;
        if (request.getCustomAlias() != null && !request.getCustomAlias().isBlank()) {
            shortCode = request.getCustomAlias().trim();
            if (urlMappingRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyTakenException(shortCode);
            }
        } else {
            shortCode = null;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = request.getExpiresInDays() != null
                ? now.plusDays(request.getExpiresInDays())
                : null;

        // When no custom alias is given, the final short code is derived from the
        // row's own auto-increment id (see below), so we can't know it before the
        // insert. A fixed placeholder like "PENDING" would collide under concurrent
        // requests since short_code is UNIQUE - use a UUID instead, which is safe
        // to insert concurrently and gets overwritten immediately after.
        String placeholder = "tmp-" + UUID.randomUUID();

        UrlMapping mapping = UrlMapping.builder()
                .shortCode(shortCode == null ? placeholder : shortCode)
                .longUrl(request.getLongUrl())
                .createdBy(owner != null ? owner.getUsername() : request.getCreatedBy())
                .owner(owner)
                .createdAt(now)
                .expiresAt(expiresAt)
                .active(true)
                .build();

        mapping = urlMappingRepository.save(mapping);

        if (shortCode == null) {
            shortCode = Base62Encoder.encode(mapping.getId());
            mapping.setShortCode(shortCode);
            urlMappingRepository.save(mapping);
        }

        urlCacheService.put(shortCode, mapping.getLongUrl(), mapping.getExpiresAt());
        clickCounterService.seedTotal(shortCode, 0);

        return toResponse(mapping);
    }

    /**
     * Hot path: resolve a short code to its target URL and record the click.
     * Cache-aside read (Redis first, DB on miss) keeps this fast; the click is
     * recorded via a fire-and-forget Kafka publish + Redis counter increment so
     * neither adds latency to the redirect itself.
     */
    public String resolveAndTrack(String shortCode, String ipAddress, String userAgent, String referrer) {
        String longUrl = urlCacheService.get(shortCode);

        if (longUrl == null) {
            UrlMapping mapping = urlMappingRepository.findByShortCodeAndActiveTrue(shortCode)
                    .filter(m -> !m.isExpired())
                    .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
            longUrl = mapping.getLongUrl();
            urlCacheService.put(shortCode, longUrl, mapping.getExpiresAt());
        }

        clickCounterService.increment(shortCode);
        clickEventProducer.publish(ClickEventMessage.builder()
                .shortCode(shortCode)
                .clickedAt(LocalDateTime.now())
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .referrer(referrer)
                .build());

        return longUrl;
    }

    public UrlMapping getMappingOrThrow(String shortCode) {
        return urlMappingRepository.findByShortCodeAndActiveTrue(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
    }

    public List<ShortenResponse> listForOwner(User owner) {
        return urlMappingRepository.findByOwnerAndActiveTrue(owner).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ShortenResponse update(String shortCode, UpdateUrlRequest request, User requester) {
        UrlMapping mapping = getMappingOrThrow(shortCode);
        assertOwnership(mapping, requester);

        if (request.getLongUrl() != null) {
            urlValidationService.validate(request.getLongUrl());
            mapping.setLongUrl(request.getLongUrl());
        }
        if (request.getExpiresInDays() != null) {
            mapping.setExpiresAt(LocalDateTime.now().plusDays(request.getExpiresInDays()));
        }

        urlMappingRepository.save(mapping);
        // Evict rather than re-put with a possibly-stale TTL calc; the next
        // redirect will repopulate the cache with the corrected value/expiry.
        urlCacheService.evict(shortCode);

        return toResponse(mapping);
    }

    @Transactional
    public void delete(String shortCode, User requester) {
        UrlMapping mapping = getMappingOrThrow(shortCode);
        assertOwnership(mapping, requester);

        mapping.setActive(false);
        urlMappingRepository.save(mapping);
        urlCacheService.evict(shortCode);
    }

    private void assertOwnership(UrlMapping mapping, User requester) {
        boolean isAdmin = requester.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return;
        }
        if (mapping.getOwner() == null || !mapping.getOwner().getId().equals(requester.getId())) {
            throw new NotOwnerException(mapping.getShortCode());
        }
    }

    /** Exposed for AnalyticsService, which needs the same owner-or-admin check. */
    public void checkOwnership(String shortCode, User requester) {
        assertOwnership(getMappingOrThrow(shortCode), requester);
    }

    /** Overload for callers that already have the mapping loaded, to avoid a duplicate lookup. */
    public void checkOwnership(UrlMapping mapping, User requester) {
        assertOwnership(mapping, requester);
    }

    private ShortenResponse toResponse(UrlMapping mapping) {
        return ShortenResponse.builder()
                .shortCode(mapping.getShortCode())
                .shortUrl(baseUrl + "/" + mapping.getShortCode())
                .longUrl(mapping.getLongUrl())
                .qrCodeUrl(baseUrl + "/api/urls/" + mapping.getShortCode() + "/qr")
                .createdAt(mapping.getCreatedAt())
                .expiresAt(mapping.getExpiresAt())
                .build();
    }
}
