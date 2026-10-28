package com.paystream.gateway.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("paystream")
public record GatewayProperties(Downstream downstream, Security security, Cors cors, RateLimit rateLimit, Live live) {

    public record Downstream(String paymentUrl, String ledgerUrl, String auditUrl) {
    }

    public record Security(Jwt jwt) {
    }

    public record Jwt(String secret, String issuer, Duration ttl) {
        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("paystream.security.jwt.secret must be at least 256 bits (32 bytes)");
            }
        }

        public SecretKey signingKey() {
            return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        }
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record RateLimit(int requestsPerSecond, int burst, int scenarioPerMinute) {
    }

    public record Live(int maxSubscribers) {
    }
}
