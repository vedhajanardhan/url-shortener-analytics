package com.vedha.urlshortener.controller;

import com.vedha.urlshortener.dto.ShortenRequest;
import com.vedha.urlshortener.dto.ShortenResponse;
import com.vedha.urlshortener.dto.UpdateUrlRequest;
import com.vedha.urlshortener.entity.User;
import com.vedha.urlshortener.exception.RateLimitExceededException;
import com.vedha.urlshortener.service.QrCodeService;
import com.vedha.urlshortener.service.RateLimiterService;
import com.vedha.urlshortener.service.UrlShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urls")
@RequiredArgsConstructor
@Tag(name = "URLs", description = "Create, list, update, and delete short URLs")
public class UrlController {

    private final UrlShortenerService urlShortenerService;
    private final QrCodeService qrCodeService;
    private final RateLimiterService rateLimiterService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Shorten a URL (owned by the authenticated user)")
    public ResponseEntity<ShortenResponse> shorten(@Valid @RequestBody ShortenRequest request,
                                                    @AuthenticationPrincipal User currentUser,
                                                    HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        if (!rateLimiterService.allow(clientIp)) {
            throw new RateLimitExceededException("Too many requests. Try again in a minute.");
        }
        return ResponseEntity.ok(urlShortenerService.shorten(request, currentUser));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List URLs owned by the authenticated user")
    public ResponseEntity<List<ShortenResponse>> listMine(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlShortenerService.listForOwner(currentUser));
    }

    @PutMapping("/{shortCode}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a URL you own (destination and/or expiry)")
    public ResponseEntity<ShortenResponse> update(@PathVariable String shortCode,
                                                   @RequestBody UpdateUrlRequest request,
                                                   @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(urlShortenerService.update(shortCode, request, currentUser));
    }

    @DeleteMapping("/{shortCode}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Deactivate a URL you own")
    public ResponseEntity<Void> delete(@PathVariable String shortCode,
                                        @AuthenticationPrincipal User currentUser) {
        urlShortenerService.delete(shortCode, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/{shortCode}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Get the QR code PNG for a short URL (public)")
    public ResponseEntity<byte[]> getQrCode(@PathVariable String shortCode) throws Exception {
        String longUrl = urlShortenerService.getMappingOrThrow(shortCode).getLongUrl();
        byte[] png = qrCodeService.generatePng(longUrl, shortCode);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .body(png);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
