package com.paystream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentInitiated(
        UUID eventId,
        UUID paymentId,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String beneficiaryCountry,
        Instant occurredAt) implements PaymentEvent {
}
