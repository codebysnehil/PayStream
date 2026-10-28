package com.paystream.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.paystream.payment.domain.Payment;
import com.paystream.payment.domain.PaymentStatus;

public record PaymentResponse(
        UUID id,
        String debtorAccount,
        String creditorAccount,
        BigDecimal amount,
        String currency,
        String beneficiaryCountry,
        PaymentStatus status,
        Integer riskScore,
        String statusReason,
        Instant createdAt,
        Instant updatedAt) {

    static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getDebtorAccount(), p.getCreditorAccount(), p.getAmount(),
                p.getCurrency(), p.getBeneficiaryCountry(), p.getStatus(), p.getRiskScore(), p.getStatusReason(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
