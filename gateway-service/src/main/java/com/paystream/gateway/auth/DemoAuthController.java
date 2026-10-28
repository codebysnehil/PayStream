package com.paystream.gateway.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.paystream.gateway.config.GatewayProperties;

/**
 * Demo sign-in for reviewers: issues a short-lived, least-privilege JWT for an anonymous reviewer
 * identity. In a bank this endpoint is replaced by OIDC login against the corporate IdP; the rest of
 * the system only ever sees a standard bearer token.
 */
@RestController
@RequestMapping("/api/auth")
public class DemoAuthController {

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String subject) {
    }

    private final JwtEncoder encoder;
    private final GatewayProperties.Jwt jwt;

    public DemoAuthController(GatewayProperties properties) {
        this.jwt = properties.security().jwt();
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwt.signingKey()));
    }

    @PostMapping("/demo")
    public TokenResponse demoLogin() {
        Instant now = Instant.now();
        String subject = "reviewer-" + UUID.randomUUID().toString().substring(0, 8);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(jwt.ttl()))
                .claim("scope", "console payments:read payments:write")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", jwt.ttl().toSeconds(), subject);
    }
}
