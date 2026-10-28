package com.paystream.ledger.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paystream.ledger.domain.Account;
import com.paystream.ledger.domain.AccountRepository;
import com.paystream.ledger.domain.CurrencyTotals;
import com.paystream.ledger.domain.LedgerEntry;
import com.paystream.ledger.domain.LedgerEntryRepository;

/**
 * Internal, read-only ledger API (reachable inside the cluster network only).
 */
@RestController
@RequestMapping("/api/v1")
public class LedgerController {

    public record AccountView(String accountNumber, String holderName, String currency, BigDecimal balance, Instant updatedAt) {
        static AccountView from(Account a) {
            return new AccountView(a.getAccountNumber(), a.getHolderName(), a.getCurrency(), a.getBalance(), a.getUpdatedAt());
        }
    }

    public record EntryView(Long id, UUID paymentId, LedgerEntry.Direction direction, BigDecimal amount, String currency, Instant createdAt) {
        static EntryView from(LedgerEntry e) {
            return new EntryView(e.getId(), e.getPaymentId(), e.getDirection(), e.getAmount(), e.getCurrency(), e.getCreatedAt());
        }
    }

    public record IntegrityReport(boolean balanced, List<CurrencyTotals> currencies) {
    }

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    public LedgerController(AccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @GetMapping("/accounts")
    public List<AccountView> accounts() {
        return accounts.findAll(Sort.by("accountNumber")).stream().map(AccountView::from).toList();
    }

    @GetMapping("/accounts/{accountNumber}")
    public AccountView account(@PathVariable String accountNumber) {
        return accounts.findById(accountNumber)
                .map(AccountView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown account"));
    }

    @GetMapping("/accounts/{accountNumber}/entries")
    public Page<EntryView> entries(@PathVariable String accountNumber,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size) {
        return entries.findByAccountNumberOrderByIdDesc(accountNumber, PageRequest.of(page, Math.min(size, 200)))
                .map(EntryView::from);
    }

    /**
     * Control check: in a double-entry ledger total debits must equal total credits per currency.
     */
    @GetMapping("/ledger/integrity")
    public IntegrityReport integrity() {
        List<CurrencyTotals> totals = entries.totalsByCurrency(LedgerEntry.Direction.DEBIT, LedgerEntry.Direction.CREDIT);
        return new IntegrityReport(totals.stream().allMatch(CurrencyTotals::isBalanced), totals);
    }
}
