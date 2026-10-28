package com.paystream.events;

import java.time.Instant;
import java.util.UUID;

public record PaymentSettled(
        UUID eventId,
        UUID paymentId,
        Outcome outcome,
        String reason,
        Instant occurredAt) implements PaymentEvent {

    public enum Outcome {
        SETTLED,
        FAILED
    }
}
