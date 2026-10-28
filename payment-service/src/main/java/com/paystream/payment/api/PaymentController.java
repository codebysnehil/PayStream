package com.paystream.payment.api;

import java.net.URI;
import java.util.List;
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

import com.paystream.payment.service.PaymentService;
import com.paystream.payment.service.PaymentService.InitiationResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
@SecurityRequirement(name = "bearer-jwt")
public class PaymentController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Initiate a payment",
            description = "Safe to retry: repeating a request with the same Idempotency-Key returns the original payment.")
    public ResponseEntity<PaymentResponse> initiate(
            @RequestHeader(IDEMPOTENCY_KEY) @Size(min = 8, max = 100) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        InitiationResult result = paymentService.initiate(idempotencyKey, request);
        PaymentResponse body = PaymentResponse.from(result.payment());
        if (result.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(body);
        }
        return ResponseEntity.created(URI.create("/api/v1/payments/" + body.id())).body(body);
    }

    @GetMapping
    @Operation(summary = "Most recent payments, newest first")
    public List<PaymentResponse> recent(@RequestParam(defaultValue = "50") int limit) {
        return paymentService.recent(limit).stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment and its current status")
    public PaymentResponse get(@PathVariable UUID id) {
        return PaymentResponse.from(paymentService.get(id));
    }
}
