package com.paystream.payment.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import com.paystream.events.Topics;

@Configuration
public class KafkaConfig {

    @Bean
    NewTopic paymentsInitiatedTopic(@Value("${paystream.kafka.partitions}") int partitions,
                                    @Value("${paystream.kafka.replicas}") short replicas) {
        return TopicBuilder.name(Topics.PAYMENTS_INITIATED).partitions(partitions).replicas(replicas).build();
    }

    /**
     * Retries transient failures with exponential back-off, then parks the record on
     * {@code <topic>.DLT} so one poison message cannot block its partition.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
        ExponentialBackOff backOff = new ExponentialBackOff(200, 2.0);
        backOff.setMaxElapsedTime(5_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }
}
