package com.paystream.gateway.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-client-IP token buckets that protect the public demo: one general bucket for API calls and a
 * much smaller one for scenario launches, which each fan out into many payments.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, TokenBucket> general = new ConcurrentHashMap<>();
    private final Map<String, TokenBucket> scenarios = new ConcurrentHashMap<>();
    private final GatewayProperties.RateLimit limits;
    private final Counter rejected;

    public RateLimitFilter(GatewayProperties properties, MeterRegistry meterRegistry) {
        this.limits = properties.rateLimit();
        this.rejected = meterRegistry.counter("paystream.gateway.rate_limited");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || "/api/live/stream".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = request.getRemoteAddr();
        boolean isScenario = "POST".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/scenarios/");

        TokenBucket bucket = isScenario
                ? scenarios.computeIfAbsent(client, k -> new TokenBucket(limits.scenarioPerMinute(), limits.scenarioPerMinute() / 60.0))
                : general.computeIfAbsent(client, k -> new TokenBucket(limits.burst(), limits.requestsPerSecond()));

        long waitMillis = bucket.tryConsume();
        if (waitMillis == 0) {
            chain.doFilter(request, response);
            return;
        }
        rejected.increment();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(1, Duration.ofMillis(waitMillis).toSeconds())));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Rate limit exceeded, slow down a little.\"}");
    }

    /** Forget idle clients so the maps stay bounded. */
    @Scheduled(fixedDelayString = "PT5M")
    void evictIdle() {
        general.values().removeIf(TokenBucket::isFull);
        scenarios.values().removeIf(TokenBucket::isFull);
    }

    static final class TokenBucket {
        private final double capacity;
        private final double refillPerNano;
        private double tokens;
        private long lastRefill;

        TokenBucket(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerNano = refillPerSecond / 1_000_000_000.0;
            this.tokens = capacity;
            this.lastRefill = System.nanoTime();
        }

        /** @return 0 if a token was taken, otherwise the milliseconds until one is available. */
        synchronized long tryConsume() {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / refillPerNano / 1_000_000);
        }

        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        private void refill() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * refillPerNano);
            lastRefill = now;
        }
    }
}
