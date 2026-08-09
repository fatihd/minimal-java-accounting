package dev.minimalaccounting.application.transaction;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalEntry;
import dev.minimalaccounting.core.journal.JournalLine;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.core.transaction.AccountEffect;
import dev.minimalaccounting.core.transaction.AccountEffectState;
import dev.minimalaccounting.core.transaction.Transaction;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.core.transaction.TransactionState;
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
            transactionId = transactions.add(transaction("request"));
            long journalEntryId = journals.add(journal("request"));
            TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                    transactions, journals, settlements::add);

            handler.handle(new TransactionJournalized(transactionId, journalEntryId));
        }

        @Test
        void advancesTransactionToSettlement() {
            assertEquals(
                    TransactionState.SETTLING,
                    transactions.get(TransactionReference.id(transactionId))
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
            transactionId = transactions.add(transactionWithoutEffects("request"));
            long journalEntryId = journals.add(journal("request"));
            TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                    transactions, journals, settlements::add);

            handler.handle(new TransactionJournalized(transactionId, journalEntryId));
        }

        @Test
        void removesCompletedTransaction() {
            assertTrue(transactions.get(
                    TransactionReference.id(transactionId)).isEmpty());
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
        long transactionId = transactions.add(transaction("transaction-request"));
        long journalEntryId = journals.add(journal("other-request"));
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
        long transactionId = transactions.add(transaction("request"));
        TransactionJournalizedHandler handler = new TransactionJournalizedHandler(
                transactions,
                new ConcurrentJournalRepository(),
                ignored -> { });

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new TransactionJournalized(transactionId, 99)));
        assertEquals(
                TransactionState.HELD,
                transactions.get(TransactionReference.id(transactionId))
                        .orElseThrow()
                        .getState());
    }

    @Test
    void retryAfterPartialSendReissuesUnconfirmedSettlementCommands() {
        ConcurrentTransactionRepository transactions = new ConcurrentTransactionRepository();
        ConcurrentJournalRepository journals = new ConcurrentJournalRepository();
        long transactionId = transactions.add(transactionWithTwoEffects("request"));
        long journalEntryId = journals.add(journal("request"));
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
