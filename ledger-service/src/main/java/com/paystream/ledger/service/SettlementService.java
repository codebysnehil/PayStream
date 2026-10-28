package com.paystream.ledger.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paystream.events.PaymentEvent;
import com.paystream.events.PaymentScreened;
import com.paystream.events.PaymentSettled;
import com.paystream.ledger.domain.Account;
import com.paystream.ledger.domain.AccountRepository;
import com.paystream.ledger.domain.LedgerEntryRepository;
import com.paystream.ledger.domain.Settlement;
import com.paystream.ledger.domain.SettlementRepository;
import com.paystream.ledger.domain.Transfer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class SettlementService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final SettlementRepository settlements;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Timer settlementTimer;

    public SettlementService(AccountRepository accounts, LedgerEntryRepository entries, SettlementRepository settlements,
                             Clock clock, MeterRegistry meterRegistry) {
        this.accounts = accounts;
        this.entries = entries;
        this.settlements = settlements;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.settlementTimer = Timer.builder("paystream.ledger.settlement")
                .description("Time to lock accounts and post a settlement")
                .register(meterRegistry);
    }

    /**
     * Settles an approved payment exactly once. The settlement row, both journal entries and both
     * balance updates commit in a single transaction. A redelivered event finds the existing
     * settlement and gets the same outcome back, so the caller can safely re-publish it.
     */
    @Transactional
    public PaymentSettled settle(PaymentScreened payment) {
        return settlementTimer.record(() -> {
            Settlement settlement = settlements.findById(payment.paymentId())
                    .map(existing -> {
                        meterRegistry.counter("paystream.ledger.duplicates").increment();
                        return existing;
                    })
                    .orElseGet(() -> settlements.save(post(payment)));
            meterRegistry.counter("paystream.ledger.settlements", "outcome", settlement.getOutcome().name()).increment();
            return new PaymentSettled(
                    PaymentEvent.deterministicId("settled", payment.paymentId()),
                    payment.paymentId(),
                    settlement.getOutcome(),
                    settlement.getReason(),
                    settlement.getCreatedAt());
        });
    }

    private Settlement post(PaymentScreened payment) {
        Instant now = clock.instant();
        // Lock in a fixed (sorted) order so concurrent A->B and B->A transfers cannot deadlock.
        Map<String, Account> locked = new HashMap<>();
        new TreeSet<>(List.of(payment.debtorAccount(), payment.creditorAccount()))
                .forEach(number -> accounts.findForUpdateByAccountNumber(number).ifPresent(a -> locked.put(number, a)));

        Account debtor = locked.get(payment.debtorAccount());
        Account creditor = locked.get(payment.creditorAccount());
        if (debtor == null || creditor == null) {
            return Settlement.failed(payment.paymentId(), "UNKNOWN_ACCOUNT", now);
        }
        if (!debtor.getCurrency().equals(payment.currency()) || !creditor.getCurrency().equals(payment.currency())) {
            return Settlement.failed(payment.paymentId(), "CURRENCY_MISMATCH", now);
        }
        if (!debtor.canDebit(payment.amount())) {
            return Settlement.failed(payment.paymentId(), "INSUFFICIENT_FUNDS", now);
        }

        entries.saveAll(Transfer.post(payment.paymentId(), debtor, creditor, payment.amount(), now));
        return Settlement.settled(payment.paymentId(), now);
    }
}
