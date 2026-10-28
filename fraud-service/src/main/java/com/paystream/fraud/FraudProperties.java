package com.paystream.fraud;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rule thresholds are configuration, not code, so risk teams can tune them per environment.
 */
@ConfigurationProperties("paystream.fraud")
public record FraudProperties(
        int rejectThreshold,
        BigDecimal highValueThreshold,
        BigDecimal hardLimit,
        BigDecimal structuringBand,
        int velocityMaxPayments,
        Duration velocityWindow,
        Set<String> highRiskCountries) {
}
