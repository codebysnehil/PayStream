package com.paystream.audit.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.audit.domain.AuditEvent;
import com.paystream.audit.domain.AuditEventRepository;
import com.paystream.events.PaymentEvent;
import com.paystream.events.Topics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Batch consumer: one bulk write per poll instead of one round trip per event, which is what lets
 * a single audit instance keep up with all three topics.
 */
@Component
public class AuditListener {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Counter recorded;

    public AuditListener(AuditEventRepository repository, ObjectMapper objectMapper, Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.recorded = meterRegistry.counter("paystream.audit.recorded");
    }

    @KafkaListener(topics = {Topics.PAYMENTS_INITIATED, Topics.PAYMENTS_SCREENED, Topics.PAYMENTS_SETTLED})
    public void onEvents(List<ConsumerRecord<String, PaymentEvent>> records) {
        Instant now = clock.instant();
        List<AuditEvent> batch = records.stream()
                .filter(r -> r.value() != null)
                .map(r -> toAuditEvent(r, now))
                .toList();
        repository.saveAll(batch);
        recorded.increment(batch.size());
    }

    private AuditEvent toAuditEvent(ConsumerRecord<String, PaymentEvent> record, Instant now) {
        PaymentEvent event = record.value();
        return new AuditEvent(
                event.eventId().toString(),
                event.paymentId().toString(),
                event.getClass().getSimpleName(),
                record.topic(),
                record.partition(),
                record.offset(),
                event.occurredAt(),
                now,
                objectMapper.convertValue(event, MAP));
    }
}
