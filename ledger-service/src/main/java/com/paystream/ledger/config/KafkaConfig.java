package com.paystream.ledger.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.paystream.events.Topics;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic paymentsSettledTopic(@Value("${paystream.kafka.partitions}") int partitions,
                                  @Value("${paystream.kafka.replicas}") short replicas) {
        return TopicBuilder.name(Topics.PAYMENTS_SETTLED).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Lock timeouts and deadlock victims are transient: retry them with back-off before giving up
     * to the dead-letter topic.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(200, 2.0);
        backOff.setMaxElapsedTime(10_000);
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
        handler.addRetryableExceptions(PessimisticLockingFailureException.class);
        return handler;
    }
}
