package com.paystream.gateway.live;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.paystream.events.PaymentEvent;
import com.paystream.events.Topics;
import com.paystream.gateway.config.GatewayProperties;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Bridges Kafka to browsers over Server-Sent Events. Each gateway instance consumes every topic
 * with its own consumer group and fans events out to its connected clients; a stats snapshot is
 * pushed once a second.
 */
@Component
public class LiveBroadcaster {

    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final Set<SseEmitter> subscribers = ConcurrentHashMap.newKeySet();
    private final LiveStats stats;
    private final int maxSubscribers;

    public LiveBroadcaster(LiveStats stats, GatewayProperties properties, MeterRegistry meterRegistry) {
        this.stats = stats;
        this.maxSubscribers = properties.live().maxSubscribers();
        Gauge.builder("paystream.gateway.sse.subscribers", subscribers, Set::size).register(meterRegistry);
    }

    public SseEmitter subscribe() {
        if (subscribers.size() >= maxSubscribers) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many live subscribers");
        }
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(e -> subscribers.remove(emitter));
        subscribers.add(emitter);
        send(emitter, "stats", stats.snapshot());
        return emitter;
    }

    @KafkaListener(topics = {Topics.PAYMENTS_INITIATED, Topics.PAYMENTS_SCREENED, Topics.PAYMENTS_SETTLED})
    public void onEvent(PaymentEvent event) {
        stats.record(event);
        LiveEvent view = LiveEvent.from(event);
        subscribers.forEach(emitter -> send(emitter, "payment", view));
    }

    @Scheduled(fixedRate = 1_000)
    public void pushStats() {
        if (!subscribers.isEmpty()) {
            LiveStats.Snapshot snapshot = stats.snapshot();
            subscribers.forEach(emitter -> send(emitter, "stats", snapshot));
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            subscribers.remove(emitter);
        }
    }
}
