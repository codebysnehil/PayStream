package com.paystream.ledger.domain;

import java.math.BigDecimal;

public record CurrencyTotals(String currency, BigDecimal debits, BigDecimal credits) {

    public boolean isBalanced() {
        return debits.compareTo(credits) == 0;
    }
}
