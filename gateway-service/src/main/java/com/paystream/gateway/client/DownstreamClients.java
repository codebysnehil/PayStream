package com.paystream.gateway.client;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.paystream.gateway.config.GatewayProperties;

/**
 * Thin, explicit clients for the internal services. Only whitelisted endpoints are exposed (no
 * generic pass-through proxy), and the caller's bearer token is relayed so downstream services
 * enforce their own authorisation. Response bodies are passed through as JSON trees: the gateway
 * does not own those contracts.
 */
@Component
public class DownstreamClients {

    private final RestClient payments;
    private final RestClient ledger;
    private final RestClient audit;

    public DownstreamClients(RestClient.Builder builder, GatewayProperties properties) {
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.payments = builder.clone().requestFactory(factory).baseUrl(properties.downstream().paymentUrl()).build();
        this.ledger = builder.clone().requestFactory(factory).baseUrl(properties.downstream().ledgerUrl()).build();
        this.audit = builder.clone().requestFactory(factory).baseUrl(properties.downstream().auditUrl()).build();
    }

    public JsonNode recentPayments(int limit) {
        return get(payments, "/api/v1/payments?limit={limit}", limit);
    }

    public JsonNode payment(UUID id) {
        return get(payments, "/api/v1/payments/{id}", id);
    }

    public ResponseEntity<JsonNode> createPayment(String token, String idempotencyKey, Map<String, Object> body) {
        return payments.post().uri("/api/v1/payments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(JsonNode.class);
    }

    public JsonNode journey(UUID paymentId) {
        return get(audit, "/api/v1/audit/payments/{id}", paymentId);
    }

    public JsonNode accounts() {
        return get(ledger, "/api/v1/accounts");
    }

    public JsonNode entries(String accountNumber, int page, int size) {
        return get(ledger, "/api/v1/accounts/{account}/entries?page={page}&size={size}", accountNumber, page, size);
    }

    public JsonNode integrity() {
        return get(ledger, "/api/v1/ledger/integrity");
    }

    public static String currentToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getTokenValue();
        }
        throw new IllegalStateException("No authenticated user");
    }

    private static JsonNode get(RestClient client, String uri, Object... vars) {
        return client.get().uri(uri, vars)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentToken())
                .retrieve()
                .body(JsonNode.class);
    }
}
