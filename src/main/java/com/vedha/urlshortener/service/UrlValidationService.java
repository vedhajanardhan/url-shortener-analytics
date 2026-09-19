package com.vedha.urlshortener.service;

import com.vedha.urlshortener.exception.InvalidUrlException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Validates a candidate long URL before it's persisted. Deliberately stricter
 * than "is this a syntactically valid URI" - also blocks scheme abuse
 * (javascript:/data: URIs redirected to would execute in the visitor's browser
 * context) and self-referential loops (shortening one of our own short links).
 */
@Slf4j
@Service
public class UrlValidationService {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_LENGTH = 2048;

    @Value("${app.base-url}")
    private String baseUrl;

    public void validate(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new InvalidUrlException("longUrl must not be blank");
        }
        if (longUrl.length() > MAX_LENGTH) {
            throw new InvalidUrlException("longUrl must not exceed " + MAX_LENGTH + " characters");
        }

        URI uri;
        try {
            uri = new URI(longUrl);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("longUrl is not a valid URI: " + ex.getReason());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("longUrl must use http or https");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("longUrl must include a valid host");
        }

        if (pointsBackAtThisService(uri)) {
            throw new InvalidUrlException("longUrl cannot point back at this shortener (would create a redirect loop)");
        }
    }

    private boolean pointsBackAtThisService(URI candidate) {
        try {
            URI base = new URI(baseUrl);
            return base.getHost() != null
                    && base.getHost().equalsIgnoreCase(candidate.getHost())
                    && base.getPort() == candidate.getPort();
        } catch (URISyntaxException ex) {
            log.warn("app.base-url is not a valid URI, skipping self-reference check: {}", ex.getMessage());
            return false;
        }
    }
}
