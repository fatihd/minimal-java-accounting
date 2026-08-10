package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionStartTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Money AMOUNT = new Money(100, USD);

    @Nested
    class StartedTransaction {
        private Transaction transaction;

        @BeforeEach
        void start() {
            transaction = transaction(
                    line("DEBIT001", DebitCredit.DEBIT, 100),
                    line("CREDIT01", DebitCredit.CREDIT, 100));

            transaction.start(Instant.EPOCH);
        }

        @Test
        void entersHoldPlacement() {
            assertEquals(TransactionState.PLACING_HOLDS, transaction.getState());
        }

        @Test
        void createsOneEffectPerAffectedAccount() {
            assertEquals(2, transaction.getEffects().size());
        }

        @Test
        void exposesEffectsAsReadOnly() {
            assertThrows(
                    UnsupportedOperationException.class,
                    () -> transaction.getEffects().clear());
        }
    }

    @Test
    void netsMultipleLinesForOneAccount() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 150),
                line("DEBIT001", DebitCredit.CREDIT, 50),
                line("CREDIT01", DebitCredit.CREDIT, 100));
        transaction.start(Instant.EPOCH);

        assertEquals(AMOUNT,
                transaction.getEffects().get(new AccountNumber("DEBIT001"))
                        .getLedgerAmount());
    }

    @Test
    void exposesDistinctReferencedAccountNumbers() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 150),
                line("DEBIT001", DebitCredit.CREDIT, 50),
                line("CREDIT01", DebitCredit.CREDIT, 100));

        assertEquals(
                Set.of(
                        new AccountNumber("DEBIT001"),
                        new AccountNumber("CREDIT01")),
                transaction.accountNumbers());
    }

    @Test
    void storesAccountEffectsInCanonicalAccountNumberOrder() {
        Transaction transaction = transaction(
                line("ZZZZZZZ1", DebitCredit.DEBIT, 100),
                line("AAAAAAAA", DebitCredit.CREDIT, 100));

        transaction.start(Instant.EPOCH);

        assertEquals(
                List.of(new AccountNumber("AAAAAAAA"), new AccountNumber("ZZZZZZZ1")),
                List.copyOf(transaction.getEffects().keySet()));
    }

    @Test
    void startsHeldWhenAllAccountEffectsNetToZero() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100),
                line("DEBIT001", DebitCredit.CREDIT, 100));

        transaction.start(Instant.EPOCH);

        assertEquals(TransactionState.HELD, transaction.getState());
        assertEquals(Instant.EPOCH, transaction.getHeldAt());
        assertEquals(Map.of(), transaction.getEffects());
    }

    @Test
    void cannotStartAnAlreadyStartedTransaction() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100),
                line("CREDIT01", DebitCredit.CREDIT, 100));
        transaction.start(Instant.EPOCH);

        assertThrows(
                IllegalStateException.class,
                () -> transaction.start(Instant.EPOCH.plusSeconds(1)));
        assertEquals(TransactionState.PLACING_HOLDS, transaction.getState());
    }

    @Test
    void rejectsFewerThanTwoLines() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100));

        assertThrows(
                PostingRejectedException.class,
                () -> transaction.start(Instant.EPOCH));
    }

    @Test
    void rejectsReconstructedTransactionWithoutLines() {
        Transaction transaction = new Transaction();
        transaction.setRequestId("request");
        transaction.setDocumentType("TRANSFER");
        transaction.setDocumentNumber("document");
        transaction.setOccurredAt(Instant.EPOCH);
        transaction.setLines(null);

        assertThrows(
                PostingRejectedException.class,
                () -> transaction.start(Instant.EPOCH));
    }

    @Test
    void rejectsLedgerLinesWithDifferentCurrencies() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100, USD),
                line("CREDIT01", DebitCredit.CREDIT, 100, EUR));

        assertThrows(
                PostingRejectedException.class,
                () -> transaction.start(Instant.EPOCH));
    }

    @Test
    void rejectsMissingOrBlankRequiredText() {
        Transaction missingRequest = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100),
                line("CREDIT01", DebitCredit.CREDIT, 100));
        missingRequest.setRequestId(null);
        Transaction blankDocumentType = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100),
                line("CREDIT01", DebitCredit.CREDIT, 100));
        blankDocumentType.setDocumentType(" ");

        assertThrows(
                PostingRejectedException.class,
                () -> missingRequest.start(Instant.EPOCH));
        assertThrows(
                PostingRejectedException.class,
                () -> blankDocumentType.start(Instant.EPOCH));
    }

    @Test
    void onlyHeldTransactionsAreJournalizable() {
        Transaction transaction = transaction(
                line("DEBIT001", DebitCredit.DEBIT, 100),
                line("CREDIT01", DebitCredit.CREDIT, 100));
        transaction.start(Instant.EPOCH);

        assertThrows(
                IllegalStateException.class,
                transaction::requireJournalizable);
    }

    private static Transaction transaction(TransactionLine... lines) {
        return new Transaction(
                null,
                null,
                null,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                null,
                null,
                null,
                List.of(lines),
                Map.of());
    }

    private static TransactionLine line(
            String accountNumber,
            DebitCredit side,
            long amount) {
        return line(accountNumber, side, amount, USD);
    }

    private static TransactionLine line(
            String accountNumber,
            DebitCredit side,
            long amount,
            Currency currency) {
        Money money = new Money(amount, currency);
        return new TransactionLine(
                new AccountNumber(accountNumber),
                side,
                money,
                money);
    }
}
