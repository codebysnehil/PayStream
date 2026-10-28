package com.paystream.events;

/**
 * Kafka topic names. Every topic is keyed by the debtor account so that all events
 * for one account land on the same partition and are processed in order.
 */
public final class Topics {

    public static final String PAYMENTS_INITIATED = "payments.initiated";
    public static final String PAYMENTS_SCREENED = "payments.screened";
    public static final String PAYMENTS_SETTLED = "payments.settled";

    private Topics() {
    }
}
