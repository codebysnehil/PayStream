package com.paystream.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;
import com.paystream.payment.domain.Payment;
import com.paystream.payment.domain.PaymentRepository;

/**
 * Applies downstream results to the payment aggregate. Duplicate and stale events are ignored by
 * the state machine, which makes every handler here idempotent.
 */
@Service
public class PaymentStatusService {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusService.class);

    private final PaymentRepository payments;

    public PaymentStatusService(PaymentRepository payments) {
        this.payments = payments;
    }

    @Transactional
    public void applyScreening(PaymentScreened event) {
        Payment payment = load(event.paymentId());
        boolean approved = event.decision() == PaymentScreened.Decision.APPROVED;
        String reason = event.reasons().isEmpty() ? null : String.join(",", event.reasons());
        if (!payment.applyScreening(approved, event.riskScore(), reason, event.occurredAt())) {
            log.info("Ignored screening result {} for payment in status {}", event.decision(), payment.getStatus());
        }
    }

    @Transactional
    public void applySettlement(PaymentSettled event) {
        Payment payment = load(event.paymentId());
        boolean settled = event.outcome() == PaymentSettled.Outcome.SETTLED;
        if (!payment.applySettlement(settled, event.reason(), event.occurredAt())) {
            log.info("Ignored settlement outcome {} for payment in status {}", event.outcome(), payment.getStatus());
        }
    }

    private Payment load(java.util.UUID id) {
        return payments.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }
}
