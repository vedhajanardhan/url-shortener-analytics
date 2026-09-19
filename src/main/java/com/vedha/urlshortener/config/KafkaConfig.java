package com.vedha.urlshortener.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.click-events-topic}")
    private String clickEventsTopic;

    @Value("${app.kafka.click-events-dlq-topic}")
    private String clickEventsDlqTopic;

    @Bean
    public NewTopic clickEventsTopic() {
        // 3 partitions so click ingestion scales horizontally with consumer instances
        return TopicBuilder.name(clickEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic clickEventsDlqTopic() {
        // Poison-pill messages (e.g. malformed payloads) land here after retries
        // are exhausted, instead of blocking the partition or being silently dropped.
        return TopicBuilder.name(clickEventsDlqTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Retry + dead-letter error handling for the click-events consumer:
     * transient failures (e.g. a momentary DB blip) get retried with backoff;
     * anything still failing after that is published to the DLQ topic rather
     * than crashing the consumer or being lost. This is what makes click
     * ingestion "reliable" beyond just the producer's acks=all/idempotence.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(clickEventsDlqTopic, record.partition()));

        ExponentialBackOff backOff = new ExponentialBackOff(500L, 2.0);
        backOff.setMaxElapsedTime(10_000L); // ~4-5 retries over 10s before giving up

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        // MANUAL_IMMEDIATE + explicit ack in the listener: the Kafka offset only
        // advances after the click has actually been persisted to MySQL, so a
        // crash mid-processing re-delivers the event instead of silently losing it.
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
