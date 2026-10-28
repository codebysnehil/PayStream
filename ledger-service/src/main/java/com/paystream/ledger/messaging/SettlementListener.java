package com.paystream.ledger.messaging;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;
import com.paystream.events.Topics;
import com.paystream.ledger.service.SettlementService;

@Component
public class SettlementListener {

    private static final Logger log = LoggerFactory.getLogger(SettlementListener.class);

    private final SettlementService settlementService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SettlementListener(SettlementService settlementService, KafkaTemplate<String, Object> kafkaTemplate) {
        this.settlementService = settlementService;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Commit-then-publish without an outbox is safe here: if the process dies after the DB commit
     * but before the broker ack, the offset is not committed, the event is redelivered, and
     * {@link SettlementService#settle} replays the stored outcome instead of posting again.
     */
    @KafkaListener(topics = Topics.PAYMENTS_SCREENED)
    public void onScreened(PaymentScreened payment) throws Exception {
        if (payment.decision() != PaymentScreened.Decision.APPROVED) {
            return;
        }
        try (var ignored = MDC.putCloseable("paymentId", payment.paymentId().toString())) {
            PaymentSettled result = settlementService.settle(payment);
            kafkaTemplate.send(Topics.PAYMENTS_SETTLED, payment.debtorAccount(), result).get(10, TimeUnit.SECONDS);
            log.info("Settlement outcome={} reason={}", result.outcome(), result.reason());
        }
    }
}
