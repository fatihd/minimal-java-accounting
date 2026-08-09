package dev.minimalaccounting.app;

import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import dev.minimalaccounting.application.account.PlaceAccountHoldHandler;
import dev.minimalaccounting.application.account.ReleaseAccountHoldHandler;
import dev.minimalaccounting.application.account.SettleAccountHoldHandler;
import dev.minimalaccounting.application.journal.JournalizeTransactionHandler;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.application.transaction.AccountHoldDeclinedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldPlacedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldReleasedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldSettledHandler;
import dev.minimalaccounting.application.transaction.TransactionIntake;
import dev.minimalaccounting.application.transaction.TransactionJournalizedHandler;

import java.time.Clock;
import java.util.List;

final class InMemoryAccounting {
    private static final int BATCH_SIZE = 20;
    private static final int MAXIMUM_POLL_CYCLES = 1_000;

    private final ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();

    private final InMemoryQueue<PlaceAccountHold> placeAccountHolds = new InMemoryQueue<>();
    private final InMemoryQueue<ReleaseAccountHold> releaseAccountHolds = new InMemoryQueue<>();
    private final InMemoryQueue<JournalizeTransaction> journalizeTransactions =
            new InMemoryQueue<>();
    private final InMemoryQueue<SettleAccountHold> settleAccountHolds = new InMemoryQueue<>();

    private final InMemoryTopic<AccountHoldPlaced> accountHoldPlaced = new InMemoryTopic<>();
    private final InMemoryTopic<AccountHoldDeclined> accountHoldDeclined = new InMemoryTopic<>();
    private final InMemoryTopic<AccountHoldReleased> accountHoldReleased = new InMemoryTopic<>();
    private final InMemoryTopic<TransactionJournalized> transactionJournalized =
            new InMemoryTopic<>();
    private final InMemoryTopic<AccountHoldSettled> accountHoldSettled = new InMemoryTopic<>();

    private final InMemoryQueue<AccountHoldPlaced> placedWorkflow =
            accountHoldPlaced.subscribe("transaction-holds");
    private final InMemoryQueue<AccountHoldDeclined> declinedWorkflow =
            accountHoldDeclined.subscribe("transaction-holds");
    private final InMemoryQueue<AccountHoldReleased> releasedWorkflow =
            accountHoldReleased.subscribe("transaction-holds");
    private final InMemoryQueue<TransactionJournalized> journalizedWorkflow =
            transactionJournalized.subscribe("transaction-settlement");
    private final InMemoryQueue<AccountHoldSettled> settledWorkflow =
            accountHoldSettled.subscribe("transaction-settlement");

    private final List<InMemoryQueue<?>> queues = List.of(
            placeAccountHolds,
            releaseAccountHolds,
            journalizeTransactions,
            settleAccountHolds,
            placedWorkflow,
            declinedWorkflow,
            releasedWorkflow,
            journalizedWorkflow,
            settledWorkflow);
    private final List<InMemoryPoller<?>> pollers;
    private final TransactionProcessor transactions;

    InMemoryAccounting(Clock clock) {
        ConcurrentTransactionRepository transactionRepository =
                new ConcurrentTransactionRepository();
        ConcurrentJournalRepository journalRepository =
                new ConcurrentJournalRepository();

        pollers = List.of(
                new InMemoryPoller<>(
                        placeAccountHolds,
                        new PlaceAccountHoldHandler(
                                accounts, accountHoldPlaced, accountHoldDeclined),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        placedWorkflow,
                        new AccountHoldPlacedHandler(
                                transactionRepository, releaseAccountHolds,
                                journalizeTransactions, clock),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        declinedWorkflow,
                        new AccountHoldDeclinedHandler(
                                transactionRepository, releaseAccountHolds),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        releaseAccountHolds,
                        new ReleaseAccountHoldHandler(accounts, accountHoldReleased),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        releasedWorkflow,
                        new AccountHoldReleasedHandler(transactionRepository),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        journalizeTransactions,
                        new JournalizeTransactionHandler(
                                accounts, transactionRepository, journalRepository,
                                transactionJournalized, clock),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        journalizedWorkflow,
                        new TransactionJournalizedHandler(
                                transactionRepository, journalRepository,
                                settleAccountHolds),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        settleAccountHolds,
                        new SettleAccountHoldHandler(accounts, accountHoldSettled),
                        BATCH_SIZE),
                new InMemoryPoller<>(
                        settledWorkflow,
                        new AccountHoldSettledHandler(transactionRepository),
                        BATCH_SIZE));

        transactions = new TransactionIntake(
                accounts,
                transactionRepository,
                journalRepository,
                placeAccountHolds,
                journalizeTransactions,
                clock);
    }

    AccountRepository accounts() {
        return accounts;
    }

    TransactionProcessor transactions() {
        return transactions;
    }

    void processUntilIdle() {
        for (int cycle = 0; cycle < MAXIMUM_POLL_CYCLES; cycle++) {
            for (InMemoryPoller<?> poller : pollers) {
                poller.pollOnce();
            }
            if (idle()) {
                return;
            }
        }
        throw new IllegalStateException(
                "in-memory messaging did not become idle after "
                        + MAXIMUM_POLL_CYCLES + " poll cycles");
    }

    private boolean idle() {
        return queues.stream().allMatch(queue -> queue.size() == 0);
    }
}
