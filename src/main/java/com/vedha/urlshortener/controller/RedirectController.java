package com.vedha.urlshortener.controller;

import com.vedha.urlshortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The hot path of the whole system: a public redirect endpoint hit far more often
 * than any other route. Kept as a thin controller with all latency-sensitive work
 * delegated to UrlShortenerService (cache-aside read + async click tracking).
 */
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlShortenerService urlShortenerService;

    // Negative lookahead excludes reserved paths (dashboard/api/actuator) so this
    // catch-all never shadows the static dashboard or REST controllers.
    @GetMapping("/{shortCode:^(?!api|dashboard|actuator)[a-zA-Z0-9_-]{3,30}$}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String ip = extractClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String referrer = request.getHeader("Referer");

        String longUrl = urlShortenerService.resolveAndTrack(shortCode, ip, userAgent, referrer);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(longUrl))
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
