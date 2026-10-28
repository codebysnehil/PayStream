package com.paystream.payment.messaging;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;
import com.paystream.events.Topics;
import com.paystream.payment.service.PaymentStatusService;

@Component
public class PaymentEventsListener {

    private final PaymentStatusService statusService;

    public PaymentEventsListener(PaymentStatusService statusService) {
        this.statusService = statusService;
    }

    @KafkaListener(topics = Topics.PAYMENTS_SCREENED)
    public void onScreened(PaymentScreened event) {
        try (var ignored = MDC.putCloseable("paymentId", event.paymentId().toString())) {
            statusService.applyScreening(event);
        }
    }

    @KafkaListener(topics = Topics.PAYMENTS_SETTLED)
    public void onSettled(PaymentSettled event) {
        try (var ignored = MDC.putCloseable("paymentId", event.paymentId().toString())) {
            statusService.applySettlement(event);
        }
    }
}
