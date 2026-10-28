package com.paystream.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;
import com.paystream.events.PaymentSettled.Outcome;
import com.paystream.ledger.domain.AccountRepository;
import com.paystream.ledger.domain.CurrencyTotals;
import com.paystream.ledger.domain.LedgerEntry;
import com.paystream.ledger.domain.LedgerEntryRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Runs against the real Flyway migrations (H2 in SQL Server compatibility mode) and the seeded
 * demo accounts.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SettlementService.class, SettlementServiceTest.Config.class})
class SettlementServiceTest {

    private static final String TREASURY = "GB82PAYS10000000000001";
    private static final String PAYROLL = "GB82PAYS10000000000002";
    private static final String RETAIL = "GB82PAYS10000000000004";
    private static final String LITWARE_EUR = "DE89PAYS20000000000001";

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    private SettlementService service;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private LedgerEntryRepository entries;

    @Test
    void settlesWithBalancedDoubleEntry() {
        BigDecimal treasuryBefore = balance(TREASURY);
        BigDecimal payrollBefore = balance(PAYROLL);

        PaymentScreened payment = approved(TREASURY, PAYROLL, "1250.50", "GBP");
        PaymentSettled result = service.settle(payment);

        assertThat(result.outcome()).isEqualTo(Outcome.SETTLED);
        assertThat(balance(TREASURY)).isEqualByComparingTo(treasuryBefore.subtract(new BigDecimal("1250.50")));
        assertThat(balance(PAYROLL)).isEqualByComparingTo(payrollBefore.add(new BigDecimal("1250.50")));

        List<LedgerEntry> posted = entries.findByPaymentId(payment.paymentId());
        assertThat(posted).extracting(LedgerEntry::getDirection)
                .containsExactlyInAnyOrder(LedgerEntry.Direction.DEBIT, LedgerEntry.Direction.CREDIT);
        assertThat(entries.totalsByCurrency(LedgerEntry.Direction.DEBIT, LedgerEntry.Direction.CREDIT))
                .allMatch(CurrencyTotals::isBalanced);
    }

    @Test
    void duplicateDeliveryPostsOnceAndReplaysTheSameOutcome() {
        BigDecimal before = balance(TREASURY);
        PaymentScreened payment = approved(TREASURY, PAYROLL, "100.00", "GBP");

        PaymentSettled first = service.settle(payment);
        PaymentSettled second = service.settle(payment);

        assertThat(second).isEqualTo(first);
        assertThat(entries.findByPaymentId(payment.paymentId())).hasSize(2);
        assertThat(balance(TREASURY)).isEqualByComparingTo(before.subtract(new BigDecimal("100.00")));
    }

    @Test
    void insufficientFundsFailsWithoutTouchingBalances() {
        BigDecimal before = balance(RETAIL);
        PaymentScreened payment = approved(RETAIL, PAYROLL, "500.01", "GBP");

        PaymentSettled result = service.settle(payment);

        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.reason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(balance(RETAIL)).isEqualByComparingTo(before);
        assertThat(entries.findByPaymentId(payment.paymentId())).isEmpty();
    }

    @Test
    void unknownAccountFails() {
        PaymentSettled result = service.settle(approved(TREASURY, "GB82PAYS99999999999999", "1.00", "GBP"));
        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.reason()).isEqualTo("UNKNOWN_ACCOUNT");
    }

    @Test
    void currencyMismatchFails() {
        PaymentSettled result = service.settle(approved(TREASURY, LITWARE_EUR, "1.00", "GBP"));
        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.reason()).isEqualTo("CURRENCY_MISMATCH");
    }

    private BigDecimal balance(String account) {
        return accounts.findById(account).orElseThrow().getBalance();
    }

    private static PaymentScreened approved(String debtor, String creditor, String amount, String currency) {
        return new PaymentScreened(UUID.randomUUID(), UUID.randomUUID(), debtor, creditor, new BigDecimal(amount),
                currency, PaymentScreened.Decision.APPROVED, 0, List.of(), Instant.now());
    }
}
