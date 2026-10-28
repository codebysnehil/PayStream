package com.paystream.fraud.messaging;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.Topics;
import com.paystream.fraud.engine.FraudEngine;

@Component
public class ScreeningListener {

    private static final Logger log = LoggerFactory.getLogger(ScreeningListener.class);

    private final FraudEngine engine;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ScreeningListener(FraudEngine engine, KafkaTemplate<String, Object> kafkaTemplate) {
        this.engine = engine;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Waits for the broker ack before returning, so the input offset is only committed once the
     * result is durably published (at-least-once; downstream de-duplicates on the event id).
     */
    @KafkaListener(topics = Topics.PAYMENTS_INITIATED)
    public void onPaymentInitiated(PaymentInitiated payment) throws Exception {
        try (var ignored = MDC.putCloseable("paymentId", payment.paymentId().toString())) {
            PaymentScreened result = engine.screen(payment);
            kafkaTemplate.send(Topics.PAYMENTS_SCREENED, payment.debtorAccount(), result).get(10, TimeUnit.SECONDS);
            log.info("Screened payment: decision={} score={} reasons={}", result.decision(), result.riskScore(), result.reasons());
        }
    }
}
