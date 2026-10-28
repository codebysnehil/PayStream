package com.paystream.fraud.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentScreened.Decision;
import com.paystream.fraud.FraudProperties;
import com.paystream.fraud.rules.HighRiskJurisdictionRule;
import com.paystream.fraud.rules.HighValueRule;
import com.paystream.fraud.rules.StructuringRule;
import com.paystream.fraud.rules.VelocityRule;
import com.paystream.fraud.velocity.VelocityTracker;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class FraudEngineTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private static final String DEBTOR = "GB82PAYS10000000000001";

    private final FraudProperties properties = new FraudProperties(70, new BigDecimal("10000"), new BigDecimal("50000"),
            new BigDecimal("0.90"), 5, Duration.ofSeconds(60), Set.of("KP", "IR"));
    private final Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
    private SimpleMeterRegistry meters;
    private FraudEngine engine;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        VelocityTracker tracker = new VelocityTracker(properties, clock);
        engine = new FraudEngine(List.of(
                new HighValueRule(properties),
                new StructuringRule(properties),
                new HighRiskJurisdictionRule(properties),
                new VelocityRule(tracker, properties)), properties, clock, meters);
    }

    @Test
    void ordinaryPaymentIsApprovedWithZeroScore() {
        PaymentScreened result = engine.screen(payment("120.00", "GB", T0));
        assertThat(result.decision()).isEqualTo(Decision.APPROVED);
        assertThat(result.riskScore()).isZero();
        assertThat(result.reasons()).isEmpty();
    }

    @Test
    void highValueAloneIsFlaggedButApproved() {
        PaymentScreened result = engine.screen(payment("15000.00", "GB", T0));
        assertThat(result.decision()).isEqualTo(Decision.APPROVED);
        assertThat(result.riskScore()).isEqualTo(40);
        assertThat(result.reasons()).containsExactly("HIGH_VALUE");
    }

    @Test
    void amountAboveHardLimitIsRejected() {
        PaymentScreened result = engine.screen(payment("50000.00", "GB", T0));
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.riskScore()).isEqualTo(100);
    }

    @Test
    void highRiskJurisdictionIsRejected() {
        PaymentScreened result = engine.screen(payment("10.00", "KP", T0));
        assertThat(result.decision()).isEqualTo(Decision.REJECTED);
        assertThat(result.reasons()).containsExactly("HIGH_RISK_JURISDICTION");
    }

    @Test
    void structuringCombinedWithVelocityIsRejected() {
        // Five payments just under the threshold are each only "possible structuring"...
        for (int i = 0; i < 5; i++) {
            assertThat(engine.screen(payment("9500.00", "GB", T0.plusSeconds(i))).decision()).isEqualTo(Decision.APPROVED);
        }
        // ...but the sixth inside the window also trips velocity, and the scores add up.
        PaymentScreened sixth = engine.screen(payment("9500.00", "GB", T0.plusSeconds(10)));
        assertThat(sixth.decision()).isEqualTo(Decision.REJECTED);
        assertThat(sixth.reasons()).containsExactlyInAnyOrder("POSSIBLE_STRUCTURING", "VELOCITY_EXCEEDED");
        assertThat(sixth.riskScore()).isEqualTo(80);
    }

    @Test
    void velocityWindowSlides() {
        for (int i = 0; i < 5; i++) {
            engine.screen(payment("10.00", "GB", T0.plusSeconds(i)));
        }
        // 61s later the earlier payments have aged out of the 60s window.
        PaymentScreened later = engine.screen(payment("10.00", "GB", T0.plusSeconds(66)));
        assertThat(later.reasons()).doesNotContain("VELOCITY_EXCEEDED");
    }

    @Test
    void redeliveredPaymentIsNotDoubleCountedAndKeepsItsEventId() {
        PaymentInitiated payment = payment("10.00", "GB", T0);
        for (int i = 0; i < 4; i++) {
            engine.screen(payment("10.00", "GB", T0.plusSeconds(i + 1)));
        }
        PaymentScreened first = engine.screen(payment);
        PaymentScreened redelivered = engine.screen(payment);

        assertThat(redelivered.reasons()).doesNotContain("VELOCITY_EXCEEDED");
        assertThat(redelivered.eventId()).isEqualTo(first.eventId());
    }

    @Test
    void recordsDecisionMetrics() {
        engine.screen(payment("10.00", "GB", T0));
        engine.screen(payment("10.00", "IR", T0));
        assertThat(meters.counter("paystream.fraud.decisions", "decision", "APPROVED").count()).isEqualTo(1);
        assertThat(meters.counter("paystream.fraud.decisions", "decision", "REJECTED").count()).isEqualTo(1);
        assertThat(meters.timer("paystream.fraud.screening").count()).isEqualTo(2);
    }

    private static PaymentInitiated payment(String amount, String country, Instant at) {
        UUID id = UUID.randomUUID();
        return new PaymentInitiated(PaymentEvent.deterministicId("initiated", id), id, DEBTOR, "GB82PAYS10000000000002",
                new BigDecimal(amount), "GBP", country, at);
    }
}
