package com.paystream.payment.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.swagger.v3.oas.annotations.Operation;

/**
 * Stand-in for a real identity provider, active only under the {@code local} profile.
 * In production tokens come from the bank's IdP (e.g. Azure AD / Ping) and this bean does not exist.
 */
@RestController
@Profile("local")
public class DevTokenController {

    private static final Duration TTL = Duration.ofHours(1);

    private final JwtEncoder encoder;
    private final JwtProperties properties;

    public DevTokenController(JwtProperties properties) {
        this.properties = properties;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey()));
    }

    @PostMapping("/auth/token")
    @Operation(summary = "Issue a short-lived developer token (local profile only)")
    public Map<String, Object> issue(@RequestParam(defaultValue = "dev-user") String subject) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim("scope", "payments:read payments:write")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", TTL.toSeconds());
    }
}
