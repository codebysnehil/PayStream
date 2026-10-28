package com.paystream.gateway.live;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.springframework.stereotype.Component;

import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;

/**
 * Real-time KPIs derived purely from the event stream: counts per stage, throughput per second,
 * reason breakdowns, and end-to-end latency (initiated to settled) measured from event timestamps.
 */
@Component
public class LiveStats {

    public record Snapshot(
            Instant since,
            long initiated,
            long approved,
            long rejected,
            long settled,
            long failed,
            Long p50LatencyMs,
            Long p95LatencyMs,
            List<Long> throughputLast60s,
            Map<String, Long> rejectionReasons,
            Map<String, Long> failureReasons) {
    }

    private static final int WINDOW_SECONDS = 120;
    private static final int LATENCY_SAMPLES = 2_000;
    private static final int MAX_IN_FLIGHT = 50_000;

    private final Clock clock;
    private final Instant since;
    private final LongAdder initiated = new LongAdder();
    private final LongAdder approved = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder settled = new LongAdder();
    private final LongAdder failed = new LongAdder();
    private final Map<String, LongAdder> rejectionReasons = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> failureReasons = new ConcurrentHashMap<>();

    private final long[] perSecond = new long[WINDOW_SECONDS];
    private final long[] perSecondStamp = new long[WINDOW_SECONDS];

    private final Map<UUID, Instant> inFlight = new LinkedHashMap<>(1024, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Instant> eldest) {
            return size() > MAX_IN_FLIGHT;
        }
    };
    private final long[] latencies = new long[LATENCY_SAMPLES];
    private final AtomicLong latencyCount = new AtomicLong();

    public LiveStats(Clock clock) {
        this.clock = clock;
        this.since = clock.instant();
    }

    public void record(PaymentEvent event) {
        switch (event) {
            case PaymentInitiated e -> {
                initiated.increment();
                tick();
                synchronized (inFlight) {
                    inFlight.put(e.paymentId(), e.occurredAt());
                }
            }
            case PaymentScreened e -> {
                if (e.decision() == PaymentScreened.Decision.APPROVED) {
                    approved.increment();
                } else {
                    rejected.increment();
                    e.reasons().forEach(r -> rejectionReasons.computeIfAbsent(r, k -> new LongAdder()).increment());
                    synchronized (inFlight) {
                        inFlight.remove(e.paymentId());
                    }
                }
            }
            case PaymentSettled e -> {
                if (e.outcome() == PaymentSettled.Outcome.SETTLED) {
                    settled.increment();
                } else {
                    failed.increment();
                    failureReasons.computeIfAbsent(String.valueOf(e.reason()), k -> new LongAdder()).increment();
                }
                Instant start;
                synchronized (inFlight) {
                    start = inFlight.remove(e.paymentId());
                }
                if (start != null) {
                    long ms = Math.max(0, e.occurredAt().toEpochMilli() - start.toEpochMilli());
                    synchronized (latencies) {
                        latencies[(int) (latencyCount.getAndIncrement() % LATENCY_SAMPLES)] = ms;
                    }
                }
            }
        }
    }

    public Snapshot snapshot() {
        long[] sample;
        synchronized (latencies) {
            sample = Arrays.copyOf(latencies, (int) Math.min(latencyCount.get(), LATENCY_SAMPLES));
        }
        Arrays.sort(sample);
        return new Snapshot(since, initiated.sum(), approved.sum(), rejected.sum(), settled.sum(), failed.sum(),
                percentile(sample, 0.50), percentile(sample, 0.95), throughput(60), sorted(rejectionReasons), sorted(failureReasons));
    }

    private void tick() {
        long second = clock.instant().getEpochSecond();
        int slot = (int) (second % WINDOW_SECONDS);
        synchronized (perSecond) {
            if (perSecondStamp[slot] != second) {
                perSecondStamp[slot] = second;
                perSecond[slot] = 0;
            }
            perSecond[slot]++;
        }
    }

    private List<Long> throughput(int seconds) {
        long now = clock.instant().getEpochSecond();
        List<Long> series = new ArrayList<>(seconds);
        synchronized (perSecond) {
            for (long s = now - seconds + 1; s <= now; s++) {
                int slot = (int) (s % WINDOW_SECONDS);
                series.add(perSecondStamp[slot] == s ? perSecond[slot] : 0L);
            }
        }
        return series;
    }

    private static Long percentile(long[] sorted, double p) {
        if (sorted.length == 0) {
            return null;
        }
        return sorted[(int) Math.min(sorted.length - 1, Math.ceil(p * sorted.length) - 1)];
    }

    private static Map<String, Long> sorted(Map<String, LongAdder> counts) {
        Map<String, Long> out = new TreeMap<>();
        counts.forEach((k, v) -> out.put(k, v.sum()));
        return out;
    }
}
