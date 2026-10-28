package com.paystream.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Common shape of every event in a payment's lifecycle.
 */
public sealed interface PaymentEvent permits PaymentInitiated, PaymentScreened, PaymentSettled {

    UUID eventId();

    UUID paymentId();

    Instant occurredAt();

    /**
     * Event ids are derived from the payment id and the lifecycle stage, so a redelivered or
     * re-emitted event carries the same id and downstream consumers can de-duplicate on it.
     */
    static UUID deterministicId(String stage, UUID paymentId) {
        return UUID.nameUUIDFromBytes((stage + ":" + paymentId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
