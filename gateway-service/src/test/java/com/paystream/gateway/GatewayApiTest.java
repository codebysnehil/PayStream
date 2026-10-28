package com.paystream.gateway;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.gateway.api.ConsoleController;
import com.paystream.gateway.api.GatewayExceptionHandler;
import com.paystream.gateway.auth.DemoAuthController;
import com.paystream.gateway.client.DownstreamClients;
import com.paystream.gateway.config.GatewayProperties;
import com.paystream.gateway.config.SecurityConfig;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Exercises the real security chain end to end: a token issued by the demo login is accepted
 * by the API, missing tokens are refused, downstream outages become 503s, and clients that
 * exceed the rate limit get 429s.
 */
@WebMvcTest(controllers = {DemoAuthController.class, ConsoleController.class})
@Import({SecurityConfig.class, GatewayExceptionHandler.class, GatewayApiTest.Metrics.class})
@ActiveProfiles("test")
class GatewayApiTest {

    @TestConfiguration
    @EnableConfigurationProperties(GatewayProperties.class)
    static class Metrics {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private DownstreamClients downstream;

    @Test
    void demoTokenGrantsAccessToConsoleApi() throws Exception {
        when(downstream.accounts()).thenReturn(objectMapper.readTree("[{\"accountNumber\":\"GB82PAYS10000000000001\"}]"));

        mvc.perform(get("/api/accounts").header("Authorization", "Bearer " + login()).with(ip("10.0.0.1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("GB82PAYS10000000000001"));
    }

    @Test
    void apiRejectsMissingOrForgedTokens() throws Exception {
        mvc.perform(get("/api/accounts").with(ip("10.0.0.2"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").header("Authorization", "Bearer not-a-jwt").with(ip("10.0.0.2")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downstreamOutageBecomesServiceUnavailable() throws Exception {
        when(downstream.integrity()).thenThrow(new ResourceAccessException("connection refused"));

        mvc.perform(get("/api/ledger/integrity").header("Authorization", "Bearer " + login()).with(ip("10.0.0.3")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void clientsOverTheRateLimitGet429WithRetryAfter() throws Exception {
        // Test profile: burst of 5, refilling at 1 request/second.
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/accounts").with(ip("10.0.0.9")));
        }
        mvc.perform(get("/api/accounts").with(ip("10.0.0.9")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/demo").with(ip("10.0.0.100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
