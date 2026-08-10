package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.AccountNumber;

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
    private AccountNumber accountNumber;
    private DebitCredit side;
    private Money transactionAmount;
    private Money ledgerAmount;
    private Money balanceAfter;

    public JournalLine(
            @NonNull AccountNumber accountNumber,
            @NonNull DebitCredit side,
            @NonNull Money transactionAmount,
            @NonNull Money ledgerAmount,
            @NonNull Money balanceAfter) {
        this.accountNumber = accountNumber;
        this.side = side;
        this.transactionAmount = transactionAmount;
        this.ledgerAmount = ledgerAmount;
        this.balanceAfter = balanceAfter;
        validate();
    }

    void validate() {
        Objects.requireNonNull(accountNumber, "accountNumber");
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
