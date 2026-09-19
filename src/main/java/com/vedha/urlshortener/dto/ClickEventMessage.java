package com.vedha.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight event published to Kafka the instant a redirect happens.
 * Kept separate from the ClickEvent entity so the hot redirect path
 * never touches JPA/Hibernate directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClickEventMessage {
    private String shortCode;
    private LocalDateTime clickedAt;
    private String ipAddress;
    private String userAgent;
    private String referrer;
}
