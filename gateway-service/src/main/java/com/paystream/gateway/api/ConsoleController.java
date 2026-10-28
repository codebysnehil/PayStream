package com.paystream.gateway.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.paystream.gateway.client.DownstreamClients;

/**
 * The console's single API surface: one origin, one auth model, regardless of how many services
 * sit behind it.
 */
@RestController
@RequestMapping("/api")
public class ConsoleController {

    private final DownstreamClients downstream;

    public ConsoleController(DownstreamClients downstream) {
        this.downstream = downstream;
    }

    @GetMapping("/payments")
    public JsonNode recentPayments(@RequestParam(defaultValue = "50") int limit) {
        return downstream.recentPayments(limit);
    }

    @GetMapping("/payments/{id}")
    public JsonNode payment(@PathVariable UUID id) {
        return downstream.payment(id);
    }

    @PostMapping("/payments")
    public ResponseEntity<JsonNode> createPayment(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                  @RequestBody Map<String, Object> body) {
        ResponseEntity<JsonNode> response = downstream.createPayment(DownstreamClients.currentToken(), idempotencyKey, body);
        ResponseEntity.BodyBuilder out = ResponseEntity.status(response.getStatusCode());
        String replayed = response.getHeaders().getFirst("Idempotent-Replayed");
        if (replayed != null) {
            out.header("Idempotent-Replayed", replayed);
        }
        return out.body(response.getBody());
    }

    @GetMapping("/payments/{id}/journey")
    public JsonNode journey(@PathVariable UUID id) {
        return downstream.journey(id);
    }

    @GetMapping("/accounts")
    public JsonNode accounts() {
        return downstream.accounts();
    }

    @GetMapping("/accounts/{accountNumber}/entries")
    public JsonNode entries(@PathVariable String accountNumber,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "25") int size) {
        return downstream.entries(accountNumber, page, size);
    }

    @GetMapping("/ledger/integrity")
    public JsonNode integrity() {
        return downstream.integrity();
    }
}
