package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.AccountNumber;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalLineTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void rejectsNonPositivePostedAmounts() {
        Money zero = Money.zero(AMOUNT.currency());

        assertThrows(IllegalArgumentException.class, () -> line(zero, AMOUNT));
        assertThrows(IllegalArgumentException.class, () -> line(AMOUNT, zero));
    }

    private static JournalLine line(
            Money transactionAmount,
            Money ledgerAmount) {
        return new JournalLine(
                new AccountNumber("ACCOUNT01"),
                DebitCredit.DEBIT,
                transactionAmount,
                ledgerAmount,
                AMOUNT);
    }
}
