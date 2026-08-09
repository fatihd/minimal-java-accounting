package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryQueue;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import lombok.NonNull;

import java.time.Clock;
import java.util.List;

final class InMemoryAccountingFactory {
    private static final int BATCH_SIZE = 20;

    private InMemoryAccountingFactory() {
    }

    static InMemoryAccounting create(@NonNull Clock clock) {
        var accounts = new ConcurrentAccountRepository();
        var transactions = new ConcurrentTransactionRepository();
        var journals = new ConcurrentJournalRepository();

        var placeAccountHolds = new InMemoryQueue<PlaceAccountHold>();
        var releaseAccountHolds = new InMemoryQueue<ReleaseAccountHold>();
        var journalizeTransactions = new InMemoryQueue<JournalizeTransaction>();
        var settleAccountHolds = new InMemoryQueue<SettleAccountHold>();

        var accountHoldsPlaced = new InMemoryTopic<AccountHoldPlaced>();
        var accountHoldsDeclined = new InMemoryTopic<AccountHoldDeclined>();
        var accountHoldsReleased = new InMemoryTopic<AccountHoldReleased>();
        var transactionsJournalized = new InMemoryTopic<TransactionJournalized>();
        var accountHoldsSettled = new InMemoryTopic<AccountHoldSettled>();

        var placedWorkflow = accountHoldsPlaced.subscribe("transaction-holds");
        var declinedWorkflow = accountHoldsDeclined.subscribe("transaction-holds");
        var releasedWorkflow = accountHoldsReleased.subscribe("transaction-holds");
        var journalizedWorkflow =
                transactionsJournalized.subscribe("transaction-settlement");
        var settledWorkflow = accountHoldsSettled.subscribe("transaction-settlement");

        var components = AccountingComponentsFactory.create(
                new AccountingRepositories(accounts, transactions, journals),
                new AccountingSenders(
                        placeAccountHolds,
                        releaseAccountHolds,
                        journalizeTransactions,
                        settleAccountHolds,
                        accountHoldsPlaced,
                        accountHoldsDeclined,
                        accountHoldsReleased,
                        transactionsJournalized,
                        accountHoldsSettled),
                clock);

        List<InMemoryPoller<?>> pollers = List.of(
                poller(placeAccountHolds, components.placeAccountHold()),
                poller(placedWorkflow, components.accountHoldPlaced()),
                poller(declinedWorkflow, components.accountHoldDeclined()),
                poller(releaseAccountHolds, components.releaseAccountHold()),
                poller(releasedWorkflow, components.accountHoldReleased()),
                poller(journalizeTransactions, components.journalizeTransaction()),
                poller(journalizedWorkflow, components.transactionJournalized()),
                poller(settleAccountHolds, components.settleAccountHold()),
                poller(settledWorkflow, components.accountHoldSettled()));

        return new InMemoryAccounting(
                accounts,
                components.transactions(),
                List.of(
                        placeAccountHolds,
                        releaseAccountHolds,
                        journalizeTransactions,
                        settleAccountHolds,
                        placedWorkflow,
                        declinedWorkflow,
                        releasedWorkflow,
                        journalizedWorkflow,
                        settledWorkflow),
                pollers);
    }

    private static <T> InMemoryPoller<T> poller(
            InMemoryQueue<T> receiver,
            dev.minimalaccounting.ports.messaging.Handler<T> handler) {
        return new InMemoryPoller<>(receiver, handler, BATCH_SIZE);
    }
}
