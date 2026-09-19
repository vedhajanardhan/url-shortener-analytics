package com.vedha.urlshortener.service;

import com.vedha.urlshortener.dto.AnalyticsSummaryResponse;
import com.vedha.urlshortener.entity.UrlMapping;
import com.vedha.urlshortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Two sources of click counts exist by design (see ClickCounterService): Redis for
 * instant "live" numbers, MySQL (populated async via Kafka) for durable historical
 * breakdowns. This service is where they're reconciled:
 *   - per-request: if Redis is unavailable, totalClicks falls back to a MySQL
 *     COUNT(*) instead of returning a stale or zeroed number.
 *   - on a schedule: periodically re-seeds the Redis counter from the MySQL count
 *     for any short code with recent activity, correcting drift from a dropped
 *     Redis increment or a Kafka message that landed in the DLQ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlShortenerService urlShortenerService;
    private final ClickCounterService clickCounterService;

    public AnalyticsSummaryResponse getSummary(String shortCode, int daysBack, com.vedha.urlshortener.entity.User requester) {
        UrlMapping mapping = urlShortenerService.getMappingOrThrow(shortCode);
        urlShortenerService.checkOwnership(mapping, requester);

        long redisTotal = clickCounterService.getTotal(shortCode);
        long totalClicks;
        String source;
        if (redisTotal == ClickCounterService.UNAVAILABLE) {
            totalClicks = clickEventRepository.countByShortCode(shortCode);
            source = "mysql-durable";
        } else {
            totalClicks = redisTotal;
            source = "redis-live";
        }

        Map<String, Long> clicksByDay = toStringLongMap(
                clickEventRepository.countByDaySince(shortCode, LocalDateTime.now().minusDays(daysBack)));
        Map<String, Long> clicksByDevice = toStringLongMap(clickEventRepository.countByDevice(shortCode));
        Map<String, Long> clicksByBrowser = toStringLongMap(clickEventRepository.countByBrowser(shortCode));
        Map<String, Long> clicksByCountry = toStringLongMap(clickEventRepository.countByCountry(shortCode));

        List<AnalyticsSummaryResponse.TopReferrer> topReferrers = clickEventRepository.topReferrers(shortCode)
                .stream()
                .limit(10)
                .map(row -> AnalyticsSummaryResponse.TopReferrer.builder()
                        .referrer((String) row[0])
                        .count(((Number) row[1]).longValue())
                        .build())
                .collect(Collectors.toList());

        return AnalyticsSummaryResponse.builder()
                .shortCode(shortCode)
                .longUrl(mapping.getLongUrl())
                .totalClicks(totalClicks)
                .totalClicksSource(source)
                .clicksByDay(clicksByDay)
                .clicksByDevice(clicksByDevice)
                .clicksByBrowser(clicksByBrowser)
                .clicksByCountry(clicksByCountry)
                .topReferrers(topReferrers)
                .build();
    }

    /**
     * Corrects Redis/MySQL drift for recently-active short codes. Scoped to
     * "activity in the last 24h" rather than the whole table so this stays cheap
     * regardless of how many short codes exist historically.
     */
    @Scheduled(fixedRateString = "${app.analytics.reconcile-interval-ms}")
    public void reconcileCounters() {
        List<String> activeCodes = clickEventRepository.findDistinctShortCodesWithActivitySince(
                LocalDateTime.now().minusHours(24));

        int corrected = 0;
        for (String shortCode : activeCodes) {
            long dbCount = clickEventRepository.countByShortCode(shortCode);
            long redisCount = clickCounterService.getTotal(shortCode);
            if (redisCount != ClickCounterService.UNAVAILABLE && redisCount != dbCount) {
                clickCounterService.seedTotal(shortCode, dbCount);
                corrected++;
            }
        }
        if (corrected > 0) {
            log.info("Analytics reconciliation corrected {} short code(s) out of {} checked", corrected, activeCodes.size());
        }
    }

    private Map<String, Long> toStringLongMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String key = row[0] != null ? row[0].toString() : "Unknown";
            long value = ((Number) row[1]).longValue();
            result.put(key, value);
        }
        return result;
    }
}
