package com.vedha.urlshortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsSummaryResponse {
    private String shortCode;
    private String longUrl;
    private long totalClicks;
    private String totalClicksSource; // "redis-live" or "mysql-durable" (fallback when Redis is unavailable)
    private Map<String, Long> clicksByDay;       // "2026-08-27" -> count
    private Map<String, Long> clicksByDevice;    // "Mobile"/"Desktop"/"Tablet" -> count
    private Map<String, Long> clicksByBrowser;
    private Map<String, Long> clicksByCountry;
    private List<TopReferrer> topReferrers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopReferrer {
        private String referrer;
        private long count;
    }
}
