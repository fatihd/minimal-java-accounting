package dev.minimalaccounting.core.journal;

import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JournalLineTest {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    @Test
    void rejectsAnInvalidAccountIdentity() {
        assertThrows(IllegalArgumentException.class, () -> line(0, AMOUNT, AMOUNT));
    }

    @Test
    void rejectsNonPositivePostedAmounts() {
        Money zero = Money.zero(AMOUNT.currency());

        assertThrows(IllegalArgumentException.class, () -> line(1, zero, AMOUNT));
        assertThrows(IllegalArgumentException.class, () -> line(1, AMOUNT, zero));
    }

    private static JournalLine line(
            long accountId,
            Money transactionAmount,
            Money ledgerAmount) {
        return new JournalLine(
                accountId,
                DebitCredit.DEBIT,
                transactionAmount,
                ledgerAmount,
                AMOUNT);
    }
}
