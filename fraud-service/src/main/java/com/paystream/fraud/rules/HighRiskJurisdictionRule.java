package com.paystream.fraud.rules;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.paystream.events.PaymentInitiated;
import com.paystream.fraud.FraudProperties;

@Component
public class HighRiskJurisdictionRule implements FraudRule {

    private final FraudProperties properties;

    public HighRiskJurisdictionRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<RuleHit> evaluate(PaymentInitiated payment) {
        if (properties.highRiskCountries().contains(payment.beneficiaryCountry())) {
            return Optional.of(new RuleHit("HIGH_RISK_JURISDICTION", 80));
        }
        return Optional.empty();
    }
}
