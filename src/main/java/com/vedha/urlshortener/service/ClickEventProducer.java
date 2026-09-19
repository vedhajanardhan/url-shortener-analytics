package com.vedha.urlshortener.service;

import com.vedha.urlshortener.dto.ClickEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes a click event to Kafka and returns immediately. This is what keeps the
 * redirect endpoint fast: the caller gets their 302 without waiting on a DB write,
 * UA parsing, or geo lookup - all of that happens downstream in the consumer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.click-events-topic}")
    private String topic;

    public void publish(ClickEventMessage event) {
        // Key by shortCode so all clicks for one URL land on the same partition,
        // preserving per-URL ordering for the consumer.
        kafkaTemplate.send(topic, event.getShortCode(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish click event for {}: {}", event.getShortCode(), ex.getMessage());
                    }
                });
    }
}
