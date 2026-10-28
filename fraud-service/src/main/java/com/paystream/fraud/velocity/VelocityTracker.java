package com.paystream.fraud.velocity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paystream.fraud.FraudProperties;

/**
 * Sliding-window payment counter per debtor account, driven by event time so results are
 * deterministic and replayable.
 *
 * <p>State is local to the instance. That is consistent because topics are keyed by debtor
 * account, so one account is always handled by exactly one partition owner. After a rebalance the
 * window starts empty for moved partitions, a deliberate trade-off against the latency of an
 * external store; a Kafka Streams state store or Redis would remove it.
 */
@Component
public class VelocityTracker {

    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, AccountWindow> windows = new ConcurrentHashMap<>();

    public VelocityTracker(FraudProperties properties, Clock clock) {
        this.window = properties.velocityWindow();
        this.clock = clock;
    }

    /**
     * Records the payment (once, even if redelivered) and returns how many payments the account
     * has made inside the window ending at {@code at}.
     */
    public int recordAndCount(String account, UUID paymentId, Instant at) {
        AccountWindow accountWindow = windows.computeIfAbsent(account, k -> new AccountWindow());
        synchronized (accountWindow) {
            accountWindow.evictBefore(at.minus(window));
            accountWindow.add(paymentId, at);
            return accountWindow.size();
        }
    }

    /** Drops idle accounts so memory stays bounded by the number of recently active accounts. */
    @Scheduled(fixedDelayString = "PT1M")
    public void evictIdleAccounts() {
        Instant cutoff = clock.instant().minus(window);
        windows.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                entry.getValue().evictBefore(cutoff);
                return entry.getValue().isEmpty();
            }
        });
    }

    int trackedAccounts() {
        return windows.size();
    }

    private static final class AccountWindow {
        private record Entry(UUID paymentId, Instant at) {
        }

        private final Deque<Entry> entries = new ArrayDeque<>();
        private final Set<UUID> seen = new HashSet<>();

        void add(UUID paymentId, Instant at) {
            if (seen.add(paymentId)) {
                entries.addLast(new Entry(paymentId, at));
            }
        }

        void evictBefore(Instant cutoff) {
            while (!entries.isEmpty() && entries.peekFirst().at().isBefore(cutoff)) {
                seen.remove(entries.removeFirst().paymentId());
            }
        }

        int size() {
            return entries.size();
        }

        boolean isEmpty() {
            return entries.isEmpty();
        }
    }
}
