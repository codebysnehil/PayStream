package com.paystream.gateway.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;

class LiveStatsTest {

    private static final Instant T0 = Instant.parse("2026-03-01T09:00:00Z");
    private final LiveStats stats = new LiveStats(Clock.fixed(T0, ZoneOffset.UTC));

    @Test
    void countsStagesAndMeasuresEndToEndLatency() {
        UUID ok = UUID.randomUUID();
        stats.record(initiated(ok, T0));
        stats.record(screened(ok, PaymentScreened.Decision.APPROVED, List.of()));
        stats.record(new PaymentSettled(UUID.randomUUID(), ok, PaymentSettled.Outcome.SETTLED, null, T0.plusMillis(42)));

        UUID blocked = UUID.randomUUID();
        stats.record(initiated(blocked, T0));
        stats.record(screened(blocked, PaymentScreened.Decision.REJECTED, List.of("HIGH_RISK_JURISDICTION")));

        UUID failed = UUID.randomUUID();
        stats.record(initiated(failed, T0));
        stats.record(new PaymentSettled(UUID.randomUUID(), failed, PaymentSettled.Outcome.FAILED, "INSUFFICIENT_FUNDS", T0.plusMillis(80)));

        LiveStats.Snapshot s = stats.snapshot();
        assertThat(s.initiated()).isEqualTo(3);
        assertThat(s.approved()).isEqualTo(1);
        assertThat(s.rejected()).isEqualTo(1);
        assertThat(s.settled()).isEqualTo(1);
        assertThat(s.failed()).isEqualTo(1);
        assertThat(s.rejectionReasons()).containsEntry("HIGH_RISK_JURISDICTION", 1L);
        assertThat(s.failureReasons()).containsEntry("INSUFFICIENT_FUNDS", 1L);
        assertThat(s.p50LatencyMs()).isEqualTo(42);
        assertThat(s.p95LatencyMs()).isEqualTo(80);
    }

    @Test
    void throughputSeriesCoversLastSixtySecondsWithCurrentSecondLast() {
        for (int i = 0; i < 3; i++) {
            stats.record(initiated(UUID.randomUUID(), T0));
        }
        List<Long> series = stats.snapshot().throughputLast60s();
        assertThat(series).hasSize(60);
        assertThat(series.get(59)).isEqualTo(3);
        assertThat(series.subList(0, 59)).allMatch(v -> v == 0);
    }

    @Test
    void emptyStatsHaveNoLatency() {
        assertThat(stats.snapshot().p95LatencyMs()).isNull();
    }

    private static PaymentInitiated initiated(UUID id, Instant at) {
        return new PaymentInitiated(UUID.randomUUID(), id, "GB82PAYS10000000000001", "GB82PAYS10000000000002",
                new BigDecimal("10.00"), "GBP", "GB", at);
    }

    private static PaymentScreened screened(UUID id, PaymentScreened.Decision decision, List<String> reasons) {
        return new PaymentScreened(UUID.randomUUID(), id, "GB82PAYS10000000000001", "GB82PAYS10000000000002",
                new BigDecimal("10.00"), "GBP", decision, 0, reasons, T0.plusMillis(5));
    }
}
