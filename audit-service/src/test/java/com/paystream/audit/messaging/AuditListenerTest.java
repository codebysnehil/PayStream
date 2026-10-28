package com.paystream.audit.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paystream.audit.domain.AuditEvent;
import com.paystream.audit.domain.AuditEventRepository;
import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentSettled;
import com.paystream.events.Topics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AuditListenerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T09:00:05Z");

    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AuditListener listener = new AuditListener(repository, mapper, Clock.fixed(NOW, ZoneOffset.UTC), meters);

    @Test
    @SuppressWarnings("unchecked")
    void writesOneDocumentPerEventInASingleBulkSave() {
        UUID paymentId = UUID.randomUUID();
        PaymentInitiated initiated = new PaymentInitiated(PaymentEvent.deterministicId("initiated", paymentId), paymentId,
                "GB82PAYS10000000000001", "GB82PAYS10000000000002", new BigDecimal("42.00"), "GBP", "GB",
                Instant.parse("2026-03-01T09:00:00Z"));
        PaymentSettled settled = new PaymentSettled(PaymentEvent.deterministicId("settled", paymentId), paymentId,
                PaymentSettled.Outcome.SETTLED, null, Instant.parse("2026-03-01T09:00:01Z"));

        List<ConsumerRecord<String, PaymentEvent>> batch = new ArrayList<>();
        batch.add(new ConsumerRecord<>(Topics.PAYMENTS_INITIATED, 2, 10L, "k", initiated));
        batch.add(new ConsumerRecord<>(Topics.PAYMENTS_SETTLED, 2, 11L, "k", settled));
        batch.add(new ConsumerRecord<>(Topics.PAYMENTS_SETTLED, 2, 12L, "k", null)); // undeserialisable record

        listener.onEvents(batch);

        ArgumentCaptor<List<AuditEvent>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);

        AuditEvent first = saved.getValue().get(0);
        assertThat(first.eventId()).isEqualTo(initiated.eventId().toString());
        assertThat(first.paymentId()).isEqualTo(paymentId.toString());
        assertThat(first.eventType()).isEqualTo("PaymentInitiated");
        assertThat(first.offset()).isEqualTo(10L);
        assertThat(first.recordedAt()).isEqualTo(NOW);
        assertThat(first.payload()).containsEntry("currency", "GBP");

        assertThat(meters.counter("paystream.audit.recorded").count()).isEqualTo(2);
    }
}
