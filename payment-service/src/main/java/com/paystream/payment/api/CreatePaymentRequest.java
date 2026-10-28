package com.paystream.payment.api;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreatePaymentRequest(
        @NotNull @Pattern(regexp = IBAN, message = "must be a valid IBAN") String debtorAccount,
        @NotNull @Pattern(regexp = IBAN, message = "must be a valid IBAN") String creditorAccount,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 code") String currency,
        @NotNull @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO-3166 alpha-2 code") String beneficiaryCountry) {

    static final String IBAN = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{8,30}$";

    @JsonIgnore
    @AssertTrue(message = "debtor and creditor accounts must differ")
    public boolean isDistinctAccounts() {
        return debtorAccount == null || !debtorAccount.equals(creditorAccount);
    }

    /**
     * Stable fingerprint of the request body, used to detect an idempotency key being reused
     * with a different payload.
     */
    public String fingerprint() {
        String canonical = String.join("|", debtorAccount, creditorAccount,
                amount.stripTrailingZeros().toPlainString(), currency, beneficiaryCountry);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
