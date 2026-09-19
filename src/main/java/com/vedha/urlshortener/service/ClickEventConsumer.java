package com.vedha.urlshortener.service;

import com.vedha.urlshortener.dto.ClickEventMessage;
import com.vedha.urlshortener.entity.ClickEvent;
import com.vedha.urlshortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Consumes click events off Kafka, enriches them (UA parsing, geo - geo is stubbed
 * here; swap in MaxMind GeoLite2 or an IP-lookup API for real country data), and
 * persists them for the historical analytics endpoints. Decoupling this from the
 * redirect request is what lets click ingestion scale independently of read traffic.
 *
 * Reliability: the container is configured for MANUAL_IMMEDIATE acks (see
 * KafkaConfig), so the offset only advances after {@code ack.acknowledge()} below -
 * i.e. after the event is durably in MySQL. If persistence throws, the exception
 * propagates to the container's DefaultErrorHandler, which retries with backoff
 * and, if still failing, routes the message to the dead-letter topic instead of
 * silently dropping it (the previous behavior here swallowed all exceptions,
 * which meant a DB blip would just lose the click).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickEventConsumer {

    private final ClickEventRepository clickEventRepository;
    private final UserAgentParsingService userAgentParsingService;
    private final GeoLookupService geoLookupService;

    @KafkaListener(topics = "${app.kafka.click-events-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(ClickEventMessage message, Acknowledgment ack) {
        UserAgentParsingService.ParsedUserAgent parsed = userAgentParsingService.parse(message.getUserAgent());
        String country = geoLookupService.lookupCountry(message.getIpAddress());

        ClickEvent event = ClickEvent.builder()
                .shortCode(message.getShortCode())
                .clickedAt(message.getClickedAt())
                .ipAddress(message.getIpAddress())
                .userAgent(message.getUserAgent())
                .referrer(message.getReferrer())
                .country(country)
                .deviceType(parsed.getDeviceType())
                .browser(parsed.getBrowser())
                .os(parsed.getOs())
                .build();

        clickEventRepository.save(event);
        ack.acknowledge();
    }
}
