package com.paystream.fraud.rules;

import java.util.Optional;

import com.paystream.events.PaymentInitiated;

/**
 * A single, independently testable fraud signal. New rules are added as Spring beans and are
 * picked up by the engine automatically (open/closed principle).
 */
public interface FraudRule {

    Optional<RuleHit> evaluate(PaymentInitiated payment);

    record RuleHit(String code, int score) {
    }
}
