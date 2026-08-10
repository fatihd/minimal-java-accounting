package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SubmitTransactionTest {
    @Test
    void copiesLines() {
        List<SubmitTransaction.Line> lines = new ArrayList<>(List.of(line()));
        SubmitTransaction transaction = transaction(lines);

        lines.clear();

        assertEquals(1, transaction.lines().size());
    }

    @Test
    void exposesLinesAsImmutable() {
        SubmitTransaction transaction = transaction(List.of(line()));

        assertThrows(UnsupportedOperationException.class, transaction.lines()::clear);
    }

    private static SubmitTransaction transaction(List<SubmitTransaction.Line> lines) {
        return new SubmitTransaction(
                "request", "TRANSFER", "document", "description",
                Instant.EPOCH, lines);
    }

    private static SubmitTransaction.Line line() {
        Money amount = new Money(100, Currency.getInstance("USD"));
        return new SubmitTransaction.Line(
                new AccountNumber("ACCOUNT1"),
                DebitCredit.DEBIT,
                amount,
                amount);
    }
}
