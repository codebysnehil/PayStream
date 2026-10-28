package com.paystream.payment.outbox;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.events.PaymentEvent;

@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Must run inside the caller's transaction so the event is committed atomically with the
     * business change (or not at all).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, String key, PaymentEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            repository.save(new OutboxEvent(event.paymentId(), topic, key, event.getClass().getName(), payload, clock.instant()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + event.getClass().getSimpleName(), e);
        }
    }
}
