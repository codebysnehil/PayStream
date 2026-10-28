package com.paystream.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.events.PaymentInitiated;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;
import com.paystream.events.Topics;
import com.paystream.payment.domain.PaymentRepository;
import com.paystream.payment.domain.PaymentStatus;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EmbeddedKafka(partitions = 1, topics = {Topics.PAYMENTS_INITIATED, Topics.PAYMENTS_SCREENED, Topics.PAYMENTS_SETTLED})
class PaymentFlowIntegrationTest {

    private static final String DEBTOR = "GB82PAYS10000000000001";
    private static final String CREDITOR = "GB82PAYS10000000000002";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private PaymentRepository payments;

    private Consumer<String, PaymentInitiated> consumer;
    private final List<PaymentInitiated> published = new CopyOnWriteArrayList<>();

    @BeforeAll
    void subscribe() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("it-" + UUID.randomUUID(), "true", broker);
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(),
                new JsonDeserializer<>(PaymentInitiated.class, false)).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, Topics.PAYMENTS_INITIATED);
    }

    @AfterAll
    void close() {
        consumer.close();
    }

    @Test
    void createsPaymentAndRelaysInitiatedEventThroughOutbox() throws Exception {
        UUID id = create("idem-" + UUID.randomUUID(), body("250.00", "GB"));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofMillis(500)).forEach(r -> published.add(r.value()));
            assertThat(published).anySatisfy(event -> {
                assertThat(event.paymentId()).isEqualTo(id);
                assertThat(event.amount()).isEqualByComparingTo("250.00");
                assertThat(event.debtorAccount()).isEqualTo(DEBTOR);
            });
        });
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsOriginalPayment() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        UUID first = create(key, body("10.00", "GB"));

        mvc.perform(post("/api/v1/payments").with(writer()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("10.00", "GB")))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.id").value(first.toString()));
    }

    @Test
    void reusingKeyWithDifferentBodyIsRejected() throws Exception {
        String key = "idem-" + UUID.randomUUID();
        create(key, body("10.00", "GB"));

        mvc.perform(post("/api/v1/payments").with(writer()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body("99.00", "GB")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        String invalid = """
                {"debtorAccount":"not-an-iban","creditorAccount":"%s","amount":-5,"currency":"gbp","beneficiaryCountry":"GB"}
                """.formatted(CREDITOR);
        mvc.perform(post("/api/v1/payments").with(writer()).header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.debtorAccount").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.currency").exists());
    }

    @Test
    void requiresAuthenticationAndCorrectScope() throws Exception {
        mvc.perform(post("/api/v1/payments").header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body("1.00", "GB")))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/payments").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read")))
                        .header("Idempotency-Key", "idem-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content(body("1.00", "GB")))
                .andExpect(status().isForbidden());
    }

    @Test
    void downstreamEventsDrivePaymentToSettled() throws Exception {
        UUID id = create("idem-" + UUID.randomUUID(), body("75.00", "GB"));

        kafkaTemplate.send(Topics.PAYMENTS_SCREENED, DEBTOR, new PaymentScreened(UUID.randomUUID(), id, DEBTOR, CREDITOR,
                new BigDecimal("75.00"), "GBP", PaymentScreened.Decision.APPROVED, 0, List.of(), Instant.now()));
        await().atMost(Duration.ofSeconds(15))
                .until(() -> payments.findById(id).orElseThrow().getStatus() == PaymentStatus.APPROVED);

        kafkaTemplate.send(Topics.PAYMENTS_SETTLED, DEBTOR,
                new PaymentSettled(UUID.randomUUID(), id, PaymentSettled.Outcome.SETTLED, null, Instant.now()));
        await().atMost(Duration.ofSeconds(15))
                .until(() -> payments.findById(id).orElseThrow().getStatus() == PaymentStatus.SETTLED);

        mvc.perform(get("/api/v1/payments/" + id).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    private UUID create(String key, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/payments").with(writer()).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_SCREENING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private static RequestPostProcessor writer() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_payments:write"));
    }

    private static String body(String amount, String country) {
        return """
                {"debtorAccount":"%s","creditorAccount":"%s","amount":%s,"currency":"GBP","beneficiaryCountry":"%s"}
                """.formatted(DEBTOR, CREDITOR, amount, country);
    }
}
