package com.paystream.payment.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.Topics;
import com.paystream.payment.api.CreatePaymentRequest;
import com.paystream.payment.domain.Payment;
import com.paystream.payment.domain.PaymentRepository;
import com.paystream.payment.outbox.OutboxWriter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class PaymentService {

    public record InitiationResult(Payment payment, boolean replayed) {
    }

    private final PaymentRepository payments;
    private final OutboxWriter outbox;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Counter initiatedCounter;
    private final Counter replayedCounter;

    public PaymentService(PaymentRepository payments, OutboxWriter outbox, TransactionTemplate transactionTemplate,
                          Clock clock, MeterRegistry meterRegistry) {
        this.payments = payments;
        this.outbox = outbox;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.initiatedCounter = meterRegistry.counter("paystream.payments.initiated");
        this.replayedCounter = meterRegistry.counter("paystream.payments.idempotent.replays");
    }

    /**
     * Creates the payment and its PaymentInitiated outbox event in one transaction. Retries with
     * the same key return the original payment; concurrent retries are resolved by the unique
     * constraint on idempotency_key.
     */
    public InitiationResult initiate(String idempotencyKey, CreatePaymentRequest request) {
        String fingerprint = request.fingerprint();
        var existing = payments.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), idempotencyKey, fingerprint);
        }
        try {
            Payment created = transactionTemplate.execute(status -> create(idempotencyKey, fingerprint, request));
            initiatedCounter.increment();
            return new InitiationResult(created, false);
        } catch (DataIntegrityViolationException raceLost) {
            return payments.findByIdempotencyKey(idempotencyKey)
                    .map(winner -> replay(winner, idempotencyKey, fingerprint))
                    .orElseThrow(() -> raceLost);
        }
    }

    public List<Payment> recent(int limit) {
        return payments.findAllByOrderByCreatedAtDesc(Limit.of(Math.clamp(limit, 1, 200)));
    }

    public Payment get(UUID id) {
        return payments.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    private Payment create(String idempotencyKey, String fingerprint, CreatePaymentRequest request) {
        Instant now = clock.instant();
        Payment payment = payments.saveAndFlush(Payment.initiate(idempotencyKey, fingerprint,
                request.debtorAccount(), request.creditorAccount(), request.amount(), request.currency(),
                request.beneficiaryCountry(), now));

        outbox.append(Topics.PAYMENTS_INITIATED, payment.getDebtorAccount(), new PaymentInitiated(
                PaymentEvent.deterministicId("initiated", payment.getId()), payment.getId(),
                payment.getDebtorAccount(), payment.getCreditorAccount(), payment.getAmount(),
                payment.getCurrency(), payment.getBeneficiaryCountry(), now));
        return payment;
    }

    private InitiationResult replay(Payment payment, String idempotencyKey, String fingerprint) {
        if (!payment.getRequestHash().equals(fingerprint)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        replayedCounter.increment();
        return new InitiationResult(payment, true);
    }
}
