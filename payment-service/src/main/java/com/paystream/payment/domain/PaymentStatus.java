package com.paystream.payment.domain;

/**
 * Payment lifecycle. Screening and settlement results arrive on different topics, so a
 * settlement can be observed before the screening result; PENDING_SCREENING therefore
 * accepts any next state, while terminal states accept nothing.
 */
public enum PaymentStatus {
    PENDING_SCREENING,
    APPROVED,
    REJECTED,
    SETTLED,
    FAILED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING_SCREENING -> next != PENDING_SCREENING;
            case APPROVED -> next == SETTLED || next == FAILED;
            case REJECTED, SETTLED, FAILED -> false;
        };
    }

    public boolean isTerminal() {
        return this == REJECTED || this == SETTLED || this == FAILED;
    }
}
