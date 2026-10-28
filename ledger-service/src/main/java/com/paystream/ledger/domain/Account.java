package com.paystream.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Account() {
    }

    public Account(String accountNumber, String holderName, String currency, BigDecimal balance, Instant now) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.currency = currency;
        this.balance = balance;
        this.updatedAt = now;
    }

    public boolean canDebit(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    void debit(BigDecimal amount, Instant at) {
        if (!canDebit(amount)) {
            throw new IllegalStateException("Insufficient funds on " + accountNumber);
        }
        balance = balance.subtract(amount);
        updatedAt = at;
    }

    void credit(BigDecimal amount, Instant at) {
        balance = balance.add(amount);
        updatedAt = at;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
