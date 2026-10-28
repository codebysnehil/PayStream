package com.paystream.ledger.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import com.paystream.events.PaymentSettled.Outcome;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * The recorded outcome for a payment. Its primary key is the payment id, so a duplicate delivery
 * can never post twice, and a redelivery replays the stored outcome instead of re-computing it.
 */
@Entity
@Table(name = "settlements")
public class Settlement implements Persistable<UUID> {

    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Outcome outcome;

    @Column(updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected Settlement() {
    }

    private Settlement(UUID paymentId, Outcome outcome, String reason, Instant now) {
        this.paymentId = paymentId;
        this.outcome = outcome;
        this.reason = reason;
        this.createdAt = now;
    }

    public static Settlement settled(UUID paymentId, Instant now) {
        return new Settlement(paymentId, Outcome.SETTLED, null, now);
    }

    public static Settlement failed(UUID paymentId, String reason, Instant now) {
        return new Settlement(paymentId, Outcome.FAILED, reason, now);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public UUID getId() {
        return paymentId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
