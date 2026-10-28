package com.paystream.audit.domain;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One immutable entry in a payment's audit trail. The document id is the event id, so a
 * redelivered event overwrites itself instead of creating a duplicate.
 */
@Document("audit_events")
@CompoundIndex(name = "payment_timeline", def = "{'paymentId': 1, 'occurredAt': 1}")
public record AuditEvent(
        @Id String eventId,
        String paymentId,
        String eventType,
        String topic,
        int partition,
        long offset,
        Instant occurredAt,
        Instant recordedAt,
        Map<String, Object> payload) {
}
