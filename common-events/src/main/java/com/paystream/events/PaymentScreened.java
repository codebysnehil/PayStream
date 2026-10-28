package com.paystream.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of fraud screening. Carries the full payment details so the ledger can settle
 * without calling back into the payment service.
 */
public record PaymentScreened(
        UUID eventId,
        UUID paymentId,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        Decision decision,
        int riskScore,
        List<String> reasons,
        Instant occurredAt) implements PaymentEvent {

    public enum Decision {
        APPROVED,
        REJECTED
    }
}
