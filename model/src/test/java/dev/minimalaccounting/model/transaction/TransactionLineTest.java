package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionLineTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void rejectsAnUnresolvedAccountIdentity() {
        assertThrows(
                PostingRejectedException.class,
                () -> line(0, AMOUNT, AMOUNT));
    }

    @Test
    void rejectsNonPositiveAmounts() {
        Money zero = Money.zero(AMOUNT.currency());

        assertThrows(
                PostingRejectedException.class,
                () -> line(1, zero, AMOUNT));
        assertThrows(
                PostingRejectedException.class,
                () -> line(1, AMOUNT, zero));
    }

    private static TransactionLine line(
            long accountId,
            Money transactionAmount,
            Money ledgerAmount) {
        return new TransactionLine(
                accountId,
                new AccountNumber("ACCOUNT01"),
                DebitCredit.DEBIT,
                transactionAmount,
                ledgerAmount);
    }
}
