package com.paystream.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PaymentTest {

    private static final Instant T0 = Instant.parse("2026-01-01T10:00:00Z");

    private static Payment newPayment() {
        return Payment.initiate("key-123456", "hash", "GB82PAYS10000000000001", "GB82PAYS10000000000002",
                new BigDecimal("100.00"), "GBP", "GB", T0);
    }

    @Test
    void startsPendingScreening() {
        assertThat(newPayment().getStatus()).isEqualTo(PaymentStatus.PENDING_SCREENING);
    }

    @Test
    void approvedThenSettled() {
        Payment payment = newPayment();
        assertThat(payment.applyScreening(true, 10, null, T0)).isTrue();
        assertThat(payment.applySettlement(true, null, T0)).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(payment.getRiskScore()).isEqualTo(10);
    }

    @Test
    void duplicateScreeningIsIgnored() {
        Payment payment = newPayment();
        payment.applyScreening(true, 10, null, T0);
        assertThat(payment.applyScreening(true, 10, null, T0)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void settlementArrivingBeforeScreeningStillApplies() {
        Payment payment = newPayment();
        assertThat(payment.applySettlement(true, null, T0)).isTrue();
        // The late screening result must not move a settled payment backwards.
        assertThat(payment.applyScreening(true, 5, null, T0)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(payment.getRiskScore()).isEqualTo(5);
    }

    @Test
    void rejectedPaymentCannotSettle() {
        Payment payment = newPayment();
        payment.applyScreening(false, 90, "HIGH_RISK_JURISDICTION", T0);
        assertThat(payment.applySettlement(true, null, T0)).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getStatusReason()).isEqualTo("HIGH_RISK_JURISDICTION");
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING_SCREENING, APPROVED, true",
            "PENDING_SCREENING, PENDING_SCREENING, false",
            "APPROVED, SETTLED, true",
            "APPROVED, REJECTED, false",
            "REJECTED, SETTLED, false",
            "SETTLED, FAILED, false",
            "FAILED, SETTLED, false"
    })
    void transitionTable(PaymentStatus from, PaymentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
