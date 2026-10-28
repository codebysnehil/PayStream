package com.paystream.fraud.rules;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paystream.events.PaymentInitiated;
import com.paystream.fraud.FraudProperties;

/**
 * Flags amounts just under the high-value threshold, a classic pattern for splitting a large
 * transfer to avoid review ("structuring" / "smurfing").
 */
@Component
public class StructuringRule implements FraudRule {

    private final BigDecimal lowerBound;
    private final BigDecimal threshold;

    public StructuringRule(FraudProperties properties) {
        this.threshold = properties.highValueThreshold();
        this.lowerBound = threshold.multiply(properties.structuringBand());
    }

    @Override
    public Optional<RuleHit> evaluate(PaymentInitiated payment) {
        BigDecimal amount = payment.amount();
        if (amount.compareTo(lowerBound) >= 0 && amount.compareTo(threshold) < 0) {
            return Optional.of(new RuleHit("POSSIBLE_STRUCTURING", 30));
        }
        return Optional.empty();
    }
}
