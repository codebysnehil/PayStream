package com.paystream.gateway.live;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;

/**
 * Flattened, browser-friendly view of a lifecycle event. Fields that don't apply to a stage are
 * omitted from the JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LiveEvent(
        String stage,
        UUID paymentId,
        Instant occurredAt,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String beneficiaryCountry,
        String decision,
        Integer riskScore,
        List<String> reasons,
        String outcome,
        String reason) {

    public static LiveEvent from(PaymentEvent event) {
        return switch (event) {
            case PaymentInitiated e -> new LiveEvent("INITIATED", e.paymentId(), e.occurredAt(), e.debtorAccount(),
                    e.creditorAccount(), e.amount(), e.currency(), e.beneficiaryCountry(), null, null, null, null, null);
            case PaymentScreened e -> new LiveEvent("SCREENED", e.paymentId(), e.occurredAt(), e.debtorAccount(),
                    e.creditorAccount(), e.amount(), e.currency(), null, e.decision().name(), e.riskScore(), e.reasons(),
                    null, null);
            case PaymentSettled e -> new LiveEvent("SETTLED", e.paymentId(), e.occurredAt(), null, null, null, null,
                    null, null, null, null, e.outcome().name(), e.reason());
        };
    }
}
