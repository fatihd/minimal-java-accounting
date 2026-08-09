package dev.minimalaccounting.app;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.application.account.PlaceAccountHoldHandler;
import dev.minimalaccounting.application.account.ReleaseAccountHoldHandler;
import dev.minimalaccounting.application.account.SettleAccountHoldHandler;
import dev.minimalaccounting.application.journal.JournalizeTransactionHandler;
import dev.minimalaccounting.application.transaction.AccountHoldDeclinedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldPlacedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldReleasedHandler;
import dev.minimalaccounting.application.transaction.AccountHoldSettledHandler;
import dev.minimalaccounting.application.transaction.TransactionIntake;
import dev.minimalaccounting.application.transaction.TransactionJournalizedHandler;

import java.time.Clock;
import java.util.Currency;
import java.util.List;

final class AccountingFixture {
    final ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();
    final ConcurrentTransactionRepository transactions = new ConcurrentTransactionRepository();
    final ConcurrentJournalRepository journals = new ConcurrentJournalRepository();

    final InMemoryQueue<PlaceAccountHold> placeAccountHolds = new InMemoryQueue<>();
    final InMemoryQueue<ReleaseAccountHold> releaseAccountHolds = new InMemoryQueue<>();
    final InMemoryQueue<JournalizeTransaction> journalizeTransactions = new InMemoryQueue<>();
    final InMemoryQueue<SettleAccountHold> settleAccountHolds = new InMemoryQueue<>();

    private final InMemoryTopic<AccountHoldPlaced> accountHoldPlaced = new InMemoryTopic<>();
    private final InMemoryTopic<AccountHoldDeclined> accountHoldDeclined = new InMemoryTopic<>();
    private final InMemoryTopic<AccountHoldReleased> accountHoldReleased = new InMemoryTopic<>();
    private final InMemoryTopic<TransactionJournalized> transactionJournalized =
            new InMemoryTopic<>();
    private final InMemoryTopic<AccountHoldSettled> accountHoldSettled = new InMemoryTopic<>();

    final InMemoryQueue<AccountHoldPlaced> placedWorkflow =
            accountHoldPlaced.subscribe("transaction-holds");
    final InMemoryQueue<AccountHoldDeclined> declinedWorkflow =
            accountHoldDeclined.subscribe("transaction-holds");
    final InMemoryQueue<AccountHoldReleased> releasedWorkflow =
            accountHoldReleased.subscribe("transaction-holds");
    final InMemoryQueue<TransactionJournalized> journalizedWorkflow =
            transactionJournalized.subscribe("transaction-settlement");
    final InMemoryQueue<AccountHoldSettled> settledWorkflow =
            accountHoldSettled.subscribe("transaction-settlement");

    final TransactionProcessor processor;
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

    AccountingFixture(Clock clock) {
        pollers = List.of(
                new InMemoryPoller<>(
                        placeAccountHolds,
                        new PlaceAccountHoldHandler(
                                accounts, accountHoldPlaced, accountHoldDeclined),
                        20),
                new InMemoryPoller<>(
                        placedWorkflow,
                        new AccountHoldPlacedHandler(
                                transactions, releaseAccountHolds,
                                journalizeTransactions, clock),
                        20),
                new InMemoryPoller<>(
                        declinedWorkflow,
                        new AccountHoldDeclinedHandler(transactions, releaseAccountHolds),
                        20),
                new InMemoryPoller<>(
                        releaseAccountHolds,
                        new ReleaseAccountHoldHandler(accounts, accountHoldReleased),
                        20),
                new InMemoryPoller<>(
                        releasedWorkflow,
                        new AccountHoldReleasedHandler(transactions),
                        20),
                new InMemoryPoller<>(
                        journalizeTransactions,
                        new JournalizeTransactionHandler(
                                accounts, transactions, journals,
                                transactionJournalized, clock),
                        20),
                new InMemoryPoller<>(
                        journalizedWorkflow,
                        new TransactionJournalizedHandler(
                                transactions, journals, settleAccountHolds),
                        20),
                new InMemoryPoller<>(
                        settleAccountHolds,
                        new SettleAccountHoldHandler(accounts, accountHoldSettled),
                        20),
                new InMemoryPoller<>(
                        settledWorkflow,
                        new AccountHoldSettledHandler(transactions),
                        20));
        processor = new TransactionIntake(
                accounts, transactions, journals,
                placeAccountHolds, journalizeTransactions, clock);
    }

    void addAccount(String accountNumber, long balance, Currency currency) {
        addAccount(accountNumber, balance, currency, null, null);
    }

    void addAccount(
            String accountNumber,
            long balance,
            Currency currency,
            Long minimum,
            Long maximum) {
        accounts.add(new Account(
                null,
                null,
                new AccountNumber(accountNumber),
                accountNumber,
                new Money(balance, currency),
                minimum == null ? null : new Money(minimum, currency),
                maximum == null ? null : new Money(maximum, currency),
                List.of()));
    }

    void processUntilIdle() {
        for (int cycle = 0; cycle < 100; cycle++) {
            for (InMemoryPoller<?> poller : pollers) {
                poller.pollOnce();
            }
            if (pendingMessages() == 0) {
                return;
            }
        }
        throw new AssertionError("messaging did not become idle");
    }

    int pendingMessages() {
        return queues.stream().mapToInt(InMemoryQueue::size).sum();
    }
}
