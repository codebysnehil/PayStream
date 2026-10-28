package com.paystream.gateway.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.paystream.gateway.client.DownstreamClients;
import com.paystream.gateway.scenario.Scenario.PaymentRequest;

import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ScenarioService {

    public record ScenarioRun(String scenario, String expected, int submitted, int rejectedByApi, List<UUID> paymentIds) {
    }

    private static final Logger log = LoggerFactory.getLogger(ScenarioService.class);
    private static final int MAX_CONCURRENCY = 32;

    private final DownstreamClients downstream;
    private final MeterRegistry meterRegistry;

    public ScenarioService(DownstreamClients downstream, MeterRegistry meterRegistry) {
        this.downstream = downstream;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Submits a scenario's payments through the real public API (with the caller's token), so the
     * demo exercises exactly the path a client would. Bursts fan out on virtual threads, bounded by
     * a semaphore so the demo cannot overwhelm the payment service.
     */
    public ScenarioRun run(Scenario scenario) {
        String token = DownstreamClients.currentToken();
        List<PaymentRequest> requests = scenario.requests();
        List<UUID> ids = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger apiRejections = new AtomicInteger();

        if (scenario.sequential()) {
            requests.forEach(r -> submit(token, r, ids, apiRejections));
        } else {
            Semaphore permits = new Semaphore(MAX_CONCURRENCY);
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (PaymentRequest request : requests) {
                    executor.submit(() -> {
                        permits.acquireUninterruptibly();
                        try {
                            submit(token, request, ids, apiRejections);
                        } finally {
                            permits.release();
                        }
                    });
                }
            }
        }
        meterRegistry.counter("paystream.gateway.scenarios", "scenario", scenario.name()).increment();
        return new ScenarioRun(scenario.name(), scenario.expected(), requests.size(), apiRejections.get(), List.copyOf(ids));
    }

    private void submit(String token, PaymentRequest r, List<UUID> ids, AtomicInteger apiRejections) {
        try {
            ResponseEntity<JsonNode> response = downstream.createPayment(token, UUID.randomUUID().toString(), Map.of(
                    "debtorAccount", r.debtorAccount(),
                    "creditorAccount", r.creditorAccount(),
                    "amount", r.amount(),
                    "currency", r.currency(),
                    "beneficiaryCountry", r.beneficiaryCountry()));
            ids.add(UUID.fromString(response.getBody().get("id").asText()));
        } catch (RuntimeException e) {
            apiRejections.incrementAndGet();
            log.warn("Scenario payment was not accepted: {}", e.getMessage());
        }
    }
}
