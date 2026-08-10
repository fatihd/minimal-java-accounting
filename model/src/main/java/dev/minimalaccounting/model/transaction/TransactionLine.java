package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor
public class TransactionLine {
    private AccountNumber accountNumber;
    private DebitCredit side;
    private Money transactionAmount;
    private Money ledgerAmount;

    public TransactionLine(
            @NonNull AccountNumber accountNumber,
            @NonNull DebitCredit side,
            @NonNull Money transactionAmount,
            @NonNull Money ledgerAmount) {
        this.accountNumber = accountNumber;
        this.side = side;
        this.transactionAmount = transactionAmount;
        this.ledgerAmount = ledgerAmount;
        validate();
    }

    void validate() {
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(side, "side");
        positive(Objects.requireNonNull(transactionAmount, "transactionAmount"),
                "transactionAmount");
        positive(Objects.requireNonNull(ledgerAmount, "ledgerAmount"), "ledgerAmount");
    }

    private static void positive(Money amount, String name) {
        if (amount.minor() <= 0) {
            throw new PostingRejectedException(name + " must be positive");
        }
    }
}
