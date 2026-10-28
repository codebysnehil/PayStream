package com.paystream.fraud.engine;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentScreened.Decision;
import com.paystream.fraud.FraudProperties;
import com.paystream.fraud.rules.FraudRule;
import com.paystream.fraud.rules.FraudRule.RuleHit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Runs every rule, sums the scores (capped at 100) and rejects at or above the threshold.
 * Scoring is additive so weak signals combine, e.g. structuring plus velocity rejects.
 */
@Service
public class FraudEngine {

    private static final int MAX_SCORE = 100;

    private final List<FraudRule> rules;
    private final FraudProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer screeningTimer;

    public FraudEngine(List<FraudRule> rules, FraudProperties properties, Clock clock, MeterRegistry meterRegistry) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.screeningTimer = Timer.builder("paystream.fraud.screening")
                .description("Time to evaluate all fraud rules for one payment")
                .register(meterRegistry);
    }

    public PaymentScreened screen(PaymentInitiated payment) {
        return screeningTimer.record(() -> {
            List<RuleHit> hits = rules.stream()
                    .map(rule -> rule.evaluate(payment))
                    .flatMap(Optional::stream)
                    .toList();
            int score = Math.min(MAX_SCORE, hits.stream().mapToInt(RuleHit::score).sum());
            Decision decision = score >= properties.rejectThreshold() ? Decision.REJECTED : Decision.APPROVED;

            meterRegistry.counter("paystream.fraud.decisions", "decision", decision.name()).increment();
            hits.forEach(hit -> meterRegistry.counter("paystream.fraud.rule.hits", "rule", hit.code()).increment());

            return new PaymentScreened(
                    PaymentEvent.deterministicId("screened", payment.paymentId()),
                    payment.paymentId(),
                    payment.debtorAccount(),
                    payment.creditorAccount(),
                    payment.amount(),
                    payment.currency(),
                    decision,
                    score,
                    hits.stream().map(RuleHit::code).toList(),
                    clock.instant());
        });
    }
}
