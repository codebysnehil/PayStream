package com.paystream.fraud.rules;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paystream.events.PaymentInitiated;
import com.paystream.fraud.FraudProperties;
import com.paystream.fraud.velocity.VelocityTracker;

@Component
public class VelocityRule implements FraudRule {

    private final VelocityTracker tracker;
    private final int maxPayments;

    public VelocityRule(VelocityTracker tracker, FraudProperties properties) {
        this.tracker = tracker;
        this.maxPayments = properties.velocityMaxPayments();
    }

    @Override
    public Optional<RuleHit> evaluate(PaymentInitiated payment) {
        int recent = tracker.recordAndCount(payment.debtorAccount(), payment.paymentId(), payment.occurredAt());
        if (recent > maxPayments) {
            return Optional.of(new RuleHit("VELOCITY_EXCEEDED", 50));
        }
        return Optional.empty();
    }
}
