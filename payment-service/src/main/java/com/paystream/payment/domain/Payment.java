package com.paystream.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @Column(name = "debtor_account", nullable = false, updatable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false, updatable = false)
    private String creditorAccount;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private String currency;

    @Column(name = "beneficiary_country", nullable = false, updatable = false)
    private String beneficiaryCountry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public static Payment initiate(String idempotencyKey, String requestHash, String debtorAccount,
                                   String creditorAccount, BigDecimal amount, String currency,
                                   String beneficiaryCountry, Instant now) {
        Payment payment = new Payment();
        payment.id = UUID.randomUUID();
        payment.idempotencyKey = idempotencyKey;
        payment.requestHash = requestHash;
        payment.debtorAccount = debtorAccount;
        payment.creditorAccount = creditorAccount;
        payment.amount = amount;
        payment.currency = currency;
        payment.beneficiaryCountry = beneficiaryCountry;
        payment.status = PaymentStatus.PENDING_SCREENING;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /**
     * @return {@code true} if the status changed; {@code false} for duplicate or stale events.
     */
    public boolean applyScreening(boolean approved, int riskScore, String reason, Instant at) {
        if (this.riskScore == null) {
            this.riskScore = riskScore;
        }
        return transitionTo(approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED, reason, at);
    }

    public boolean applySettlement(boolean settled, String reason, Instant at) {
        return transitionTo(settled ? PaymentStatus.SETTLED : PaymentStatus.FAILED, reason, at);
    }

    private boolean transitionTo(PaymentStatus target, String reason, Instant at) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        this.status = target;
        this.statusReason = reason;
        this.updatedAt = at;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBeneficiaryCountry() {
        return beneficiaryCountry;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Integer getRiskScore() {
        return riskScore;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
