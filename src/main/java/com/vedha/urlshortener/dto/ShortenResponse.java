package com.vedha.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {
    private String shortCode;
    private String shortUrl;
    private String longUrl;
    private String qrCodeUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
