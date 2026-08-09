package dev.minimalaccounting.core.journal;

import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public class JournalLine {
    private long accountId;
    private DebitCredit side;
    private Money transactionAmount;
    private Money ledgerAmount;
    private Money balanceAfter;

    public JournalLine(
            long accountId,
            @NonNull DebitCredit side,
            @NonNull Money transactionAmount,
            @NonNull Money ledgerAmount,
            @NonNull Money balanceAfter) {
        this.accountId = accountId;
        this.side = side;
        this.transactionAmount = transactionAmount;
        this.ledgerAmount = ledgerAmount;
        this.balanceAfter = balanceAfter;
        validate();
    }

    void validate() {
        if (accountId < 1) {
            throw new IllegalArgumentException("accountId must be positive");
        }
        Objects.requireNonNull(side, "side");
        positive(Objects.requireNonNull(transactionAmount, "transactionAmount"),
                "transactionAmount");
        positive(Objects.requireNonNull(ledgerAmount, "ledgerAmount"), "ledgerAmount");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
    }

    private static void positive(Money amount, String name) {
        if (amount.minor() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
