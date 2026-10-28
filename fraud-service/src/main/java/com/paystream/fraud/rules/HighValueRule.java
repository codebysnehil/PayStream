package com.paystream.fraud.rules;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paystream.events.PaymentInitiated;
import com.paystream.fraud.FraudProperties;

@Component
public class HighValueRule implements FraudRule {

    private final FraudProperties properties;

    public HighValueRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<RuleHit> evaluate(PaymentInitiated payment) {
        if (payment.amount().compareTo(properties.hardLimit()) >= 0) {
            return Optional.of(new RuleHit("AMOUNT_EXCEEDS_HARD_LIMIT", 100));
        }
        if (payment.amount().compareTo(properties.highValueThreshold()) >= 0) {
            return Optional.of(new RuleHit("HIGH_VALUE", 40));
        }
        return Optional.empty();
    }
}
