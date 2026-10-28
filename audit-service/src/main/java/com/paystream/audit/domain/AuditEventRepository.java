package com.paystream.audit.domain;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {

    List<AuditEvent> findByPaymentIdOrderByOccurredAtAsc(String paymentId);
}
