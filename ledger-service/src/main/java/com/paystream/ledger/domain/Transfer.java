package com.paystream.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Moves money between two locked accounts and returns the balanced pair of journal entries.
 * Kept free of persistence concerns so the double-entry invariant is unit-testable on its own.
 */
public final class Transfer {

    private Transfer() {
    }

    public static List<LedgerEntry> post(UUID paymentId, Account debtor, Account creditor, BigDecimal amount, Instant at) {
        debtor.debit(amount, at);
        creditor.credit(amount, at);
        return List.of(
                new LedgerEntry(paymentId, debtor.getAccountNumber(), LedgerEntry.Direction.DEBIT, amount, debtor.getCurrency(), at),
                new LedgerEntry(paymentId, creditor.getAccountNumber(), LedgerEntry.Direction.CREDIT, amount, creditor.getCurrency(), at));
    }
}
