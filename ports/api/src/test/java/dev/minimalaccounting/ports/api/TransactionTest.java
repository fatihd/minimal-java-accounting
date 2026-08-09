package dev.minimalaccounting.ports.api;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionTest {
    @Test
    void copiesLines() {
        List<Transaction.Line> lines = new ArrayList<>(List.of(line()));
        Transaction transaction = transaction(lines);

        lines.clear();

        assertEquals(1, transaction.lines().size());
    }

    @Test
    void exposesLinesAsImmutable() {
        Transaction transaction = transaction(List.of(line()));

        assertThrows(UnsupportedOperationException.class, transaction.lines()::clear);
    }

    private static Transaction transaction(List<Transaction.Line> lines) {
        return new Transaction(
                "request", "TRANSFER", "document", "description",
                Instant.EPOCH, lines);
    }

    private static Transaction.Line line() {
        Money amount = new Money(100, Currency.getInstance("USD"));
        return new Transaction.Line(
                new AccountNumber("ACCOUNT1"),
                DebitCredit.DEBIT,
                amount,
                amount);
    }
}
