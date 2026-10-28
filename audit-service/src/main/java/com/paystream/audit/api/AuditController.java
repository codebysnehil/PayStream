package com.paystream.audit.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paystream.audit.domain.AuditEvent;
import com.paystream.audit.domain.AuditEventRepository;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    /** Full, ordered journey of one payment across every service. */
    @GetMapping("/payments/{paymentId}")
    public List<AuditEvent> timeline(@PathVariable UUID paymentId) {
        return repository.findByPaymentIdOrderByOccurredAtAsc(paymentId.toString());
    }
}
