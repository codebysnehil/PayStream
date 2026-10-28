package com.paystream.payment.outbox;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.events.PaymentEvent;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Polls unpublished outbox rows and relays them to Kafka. Delivery is at-least-once: a crash
 * after the send but before the row is marked published re-sends the event, which every
 * consumer tolerates because event ids are deterministic and consumers are idempotent.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String EVENTS_PACKAGE = PaymentEvent.class.getPackageName() + ".";

    private final OutboxRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, Object> kafkaTemplate,
                       ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry,
                       @Value("${paystream.outbox.batch-size:200}") int batchSize) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        Gauge.builder("paystream.outbox.pending", repository, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox events not yet relayed to Kafka")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${paystream.outbox.poll-interval-ms:200}")
    public void relayPending() {
        List<OutboxEvent> batch = repository.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(batchSize));
        if (batch.isEmpty()) {
            return;
        }

        // Send the whole batch asynchronously, then wait: far higher throughput than send-and-wait per row.
        List<CompletableFuture<SendResult<String, Object>>> sends = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            sends.add(send(event));
        }

        List<UUID> published = new ArrayList<>(batch.size());
        List<UUID> failed = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                sends.get(i).get(10, TimeUnit.SECONDS);
                published.add(event.getId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.add(event.getId());
            } catch (Exception e) {
                log.warn("Outbox relay failed for event {} (attempt {}): {}", event.getId(), event.getAttempts() + 1, e.getMessage());
                failed.add(event.getId());
            }
        }
        if (!published.isEmpty()) {
            repository.markPublished(published, clock.instant());
        }
        if (!failed.isEmpty()) {
            repository.recordFailedAttempt(failed);
        }
    }

    private CompletableFuture<SendResult<String, Object>> send(OutboxEvent event) {
        try {
            if (!event.getEventType().startsWith(EVENTS_PACKAGE)) {
                throw new IllegalStateException("Refusing to deserialise untrusted type " + event.getEventType());
            }
            Object payload = objectMapper.readValue(event.getPayload(), Class.forName(event.getEventType()));
            return kafkaTemplate.send(event.getTopic(), event.getMessageKey(), payload);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
