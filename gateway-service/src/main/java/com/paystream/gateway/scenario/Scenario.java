package com.paystream.gateway.scenario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

/**
 * Pre-built demo scenarios. Each produces the payment requests to submit and states the outcome a
 * reviewer should expect to see, so the console can show "expected vs actual".
 */
public enum Scenario {

    HAPPY_PATH("Happy path", "A routine domestic transfer between two healthy accounts.", "SETTLED") {
        @Override
        List<PaymentRequest> requests() {
            return List.of(new PaymentRequest(randomLoadAccount(), "GB82PAYS10000000000003", amount(50, 2_000), "GBP", "GB"));
        }
    },
    INSUFFICIENT_FUNDS("Insufficient funds", "Retail account with £500 tries to send £900.", "FAILED · INSUFFICIENT_FUNDS") {
        @Override
        List<PaymentRequest> requests() {
            return List.of(new PaymentRequest("GB82PAYS10000000000004", "GB82PAYS10000000000002", new BigDecimal("900.00"), "GBP", "GB"));
        }
    },
    SANCTIONED_JURISDICTION("High-risk jurisdiction", "Small payment to a beneficiary in a high-risk country.", "REJECTED · HIGH_RISK_JURISDICTION") {
        @Override
        List<PaymentRequest> requests() {
            return List.of(new PaymentRequest(randomLoadAccount(), "GB82PAYS10000000000003", new BigDecimal("120.00"), "GBP", "IR"));
        }
    },
    HARD_LIMIT("Over hard limit", "A £60,000 transfer exceeds the single-payment hard limit.", "REJECTED · AMOUNT_EXCEEDS_HARD_LIMIT") {
        @Override
        List<PaymentRequest> requests() {
            return List.of(new PaymentRequest(randomLoadAccount(), "GB82PAYS10000000000003", new BigDecimal("60000.00"), "GBP", "GB"));
        }
    },
    CURRENCY_MISMATCH("Currency mismatch", "GBP payment into a EUR account.", "FAILED · CURRENCY_MISMATCH") {
        @Override
        List<PaymentRequest> requests() {
            return List.of(new PaymentRequest(randomLoadAccount(), "DE89PAYS20000000000001", new BigDecimal("75.00"), "GBP", "GB"));
        }
    },
    STRUCTURING_BURST("Structuring burst", "Six £9,500 payments in seconds from one account, just under the £10k review threshold.",
            "First five APPROVED, sixth REJECTED · POSSIBLE_STRUCTURING + VELOCITY_EXCEEDED") {
        @Override
        List<PaymentRequest> requests() {
            return IntStream.range(0, 6)
                    .mapToObj(i -> new PaymentRequest("GB82PAYS10000000000003", "GB82PAYS10000000000002", new BigDecimal("9500.00"), "GBP", "GB"))
                    .toList();
        }

        @Override
        boolean sequential() {
            return true;
        }
    },
    LOAD_BURST("Load burst", "200 concurrent payments across 40 accounts to show throughput and latency under load.",
            "Mostly SETTLED; a few VELOCITY_EXCEEDED where one account sends >5 in a minute") {
        @Override
        List<PaymentRequest> requests() {
            List<PaymentRequest> out = new ArrayList<>(200);
            for (int i = 0; i < 200; i++) {
                String debtor = randomLoadAccount();
                String creditor;
                do {
                    creditor = randomLoadAccount();
                } while (creditor.equals(debtor));
                out.add(new PaymentRequest(debtor, creditor, amount(1, 5_000), "GBP", "GB"));
            }
            return out;
        }
    };

    public record PaymentRequest(String debtorAccount, String creditorAccount, BigDecimal amount, String currency,
                                 String beneficiaryCountry) {
    }

    private final String title;
    private final String description;
    private final String expected;

    Scenario(String title, String description, String expected) {
        this.title = title;
        this.description = description;
        this.expected = expected;
    }

    abstract List<PaymentRequest> requests();

    boolean sequential() {
        return false;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public String expected() {
        return expected;
    }

    /** One of the 40 well-funded load-test accounts seeded by the ledger (GB82PAYS300000000000xx). */
    static String randomLoadAccount() {
        return "GB82PAYS3000000000%04d".formatted(ThreadLocalRandom.current().nextInt(1, 41));
    }

    private static BigDecimal amount(int min, int max) {
        return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(min, max)).setScale(2, RoundingMode.HALF_UP);
    }
}
