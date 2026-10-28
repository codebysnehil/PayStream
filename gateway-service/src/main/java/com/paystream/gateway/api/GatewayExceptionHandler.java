package com.paystream.gateway.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /** Downstream 4xx (validation, not found, idempotency conflict) are passed through unchanged. */
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<String> downstreamError(RestClientResponseException ex) {
        if (ex.getStatusCode().is5xxServerError()) {
            log.warn("Downstream error {}: {}", ex.getStatusCode(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body("{\"title\":\"Bad Gateway\",\"status\":502,\"detail\":\"A downstream service failed.\"}");
        }
        return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ex.getResponseBodyAsString());
    }

    @ExceptionHandler(ResourceAccessException.class)
    ProblemDetail unavailable(ResourceAccessException ex) {
        log.warn("Downstream unavailable: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "A downstream service is unavailable, try again shortly.");
    }
}
