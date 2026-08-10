package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.journal.JournalLine;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.model.transaction.AccountEffect;
import dev.minimalaccounting.model.transaction.AccountEffectState;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.TransactionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionJournalizedHandlerTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);

    @Nested
    class MatchingJournalEntry {
        private final ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        private final ConcurrentJournalRepository journals =
                new ConcurrentJournalRepository();
        private final List<SettleAccountHold> settlements = new ArrayList<>();
        private long transactionId;

        @BeforeEach
        void handleEvent() {
            transactionId = transactions.add(transaction("request")).id();
            long journalEntryId = journals.add(journal("request")).id();
            TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                    transactions, journals, settlements::add);

            handler.handle(new TransactionJournalized(transactionId, journalEntryId));
        }

        @Test
        void advancesTransactionToSettlement() {
            assertEquals(
                    TransactionState.SETTLING,
                    transactions.get(TransactionIdentifier.id(transactionId))
                            .orElseThrow()
                            .getState());
        }

        @Test
        void requestsSettlementForEachAccountEffect() {
            assertEquals(
                    List.of(new SettleAccountHold(
                            1, transactionId, 0, DebitCredit.DEBIT, AMOUNT)),
                    settlements);
        }
    }

    @Nested
    class TransactionWithoutAccountEffects {
        private final ConcurrentTransactionRepository transactions =
                new ConcurrentTransactionRepository();
        private final ConcurrentJournalRepository journals =
                new ConcurrentJournalRepository();
        private final List<SettleAccountHold> settlements = new ArrayList<>();
        private long transactionId;

        @BeforeEach
        void handleEvent() {
            transactionId = transactions.add(transactionWithoutEffects("request")).id();
            long journalEntryId = journals.add(journal("request")).id();
            TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                    transactions, journals, settlements::add);

            handler.handle(new TransactionJournalized(transactionId, journalEntryId));
        }

        @Test
        void removesCompletedTransaction() {
            assertTrue(transactions.get(
                    TransactionIdentifier.id(transactionId)).isEmpty());
        }

        @Test
        void doesNotRequestAccountSettlement() {
            assertTrue(settlements.isEmpty());
        }
    }

    @Test
    void rejectsJournalEntryForAnotherTransaction() {
        ConcurrentTransactionRepository transactions = new ConcurrentTransactionRepository();
        ConcurrentJournalRepository journals = new ConcurrentJournalRepository();
        long transactionId = transactions.add(transaction("transaction-request")).id();
        long journalEntryId = journals.add(journal("other-request")).id();
        TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                transactions, journals, ignored -> { });

        assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(
                        new TransactionJournalized(transactionId, journalEntryId)));
    }

    @Test
    void absentTransactionIsAcknowledgedAsStale() {
        TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                new ConcurrentTransactionRepository(),
                new ConcurrentJournalRepository(),
                ignored -> { });

        assertDoesNotThrow(
                () -> handler.handle(new TransactionJournalized(99, 88)));
    }

    @Test
    void journalThatIsNotYetAvailableNacksForRetry() {
        ConcurrentTransactionRepository transactions = new ConcurrentTransactionRepository();
        long transactionId = transactions.add(transaction("request")).id();
        TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                transactions,
                new ConcurrentJournalRepository(),
                ignored -> { });

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new TransactionJournalized(transactionId, 99)));
        assertEquals(
                TransactionState.HELD,
                transactions.get(TransactionIdentifier.id(transactionId))
                        .orElseThrow()
                        .getState());
    }

    @Test
    void retryAfterPartialSendReissuesUnconfirmedSettlementCommands() {
        ConcurrentTransactionRepository transactions = new ConcurrentTransactionRepository();
        ConcurrentJournalRepository journals = new ConcurrentJournalRepository();
        long transactionId = transactions.add(transactionWithTwoEffects("request")).id();
        long journalEntryId = journals.add(journal("request")).id();
        List<SettleAccountHold> settlements = new ArrayList<>();
        AtomicInteger sends = new AtomicInteger();
        TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                transactions,
                journals,
                command -> {
                    if (sends.incrementAndGet() == 2) {
                        throw new IllegalStateException("queue unavailable");
                    }
                    settlements.add(command);
                });
        TransactionJournalized event = new TransactionJournalized(
                transactionId, journalEntryId);

        assertThrows(IllegalStateException.class, () -> handler.handle(event));
        handler.handle(event);

        assertEquals(
                List.of(
                        new SettleAccountHold(
                                1, transactionId, 0, DebitCredit.DEBIT, AMOUNT),
                        new SettleAccountHold(
                                1, transactionId, 0, DebitCredit.DEBIT, AMOUNT),
                        new SettleAccountHold(
                                2, transactionId, 1, DebitCredit.CREDIT, AMOUNT)),
                settlements);
    }

    private static Transaction transaction(String requestId) {
        AccountEffect effect = new AccountEffect(
                1,
                new AccountNumber("ACCOUNT01"),
                DebitCredit.DEBIT,
                AMOUNT,
                AccountEffectState.HELD);
        return new Transaction(
                null,
                null,
                TransactionState.HELD,
                requestId,
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                List.of(),
                List.of(effect));
    }

    private static Transaction transactionWithoutEffects(String requestId) {
        return new Transaction(
                null,
                null,
                TransactionState.HELD,
                requestId,
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                List.of(),
                List.of());
    }

    private static Transaction transactionWithTwoEffects(String requestId) {
        return new Transaction(
                null,
                null,
                TransactionState.HELD,
                requestId,
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                List.of(),
                List.of(
                        new AccountEffect(
                                1,
                                new AccountNumber("ACCOUNT01"),
                                DebitCredit.DEBIT,
                                AMOUNT,
                                AccountEffectState.HELD),
                        new AccountEffect(
                                2,
                                new AccountNumber("ACCOUNT02"),
                                DebitCredit.CREDIT,
                                AMOUNT,
                                AccountEffectState.HELD)));
    }

    private static JournalEntry journal(String requestId) {
        return new JournalEntry(
                null,
                null,
                requestId,
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of(
                        new JournalLine(
                                1, DebitCredit.DEBIT, AMOUNT, AMOUNT,
                                new Money(1100, USD)),
                        new JournalLine(
                                2, DebitCredit.CREDIT, AMOUNT, AMOUNT,
                                new Money(900, USD))));
    }
}
