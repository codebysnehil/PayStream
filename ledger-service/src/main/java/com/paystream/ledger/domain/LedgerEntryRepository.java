package com.paystream.ledger.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Page<LedgerEntry> findByAccountNumberOrderByIdDesc(String accountNumber, Pageable pageable);

    List<LedgerEntry> findByPaymentId(UUID paymentId);

    @Query("""
            select new com.paystream.ledger.domain.CurrencyTotals(
                e.currency,
                sum(case when e.direction = :debit then e.amount else 0 end),
                sum(case when e.direction = :credit then e.amount else 0 end))
            from LedgerEntry e
            group by e.currency
            """)
    List<CurrencyTotals> totalsByCurrency(LedgerEntry.Direction debit, LedgerEntry.Direction credit);
}
