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
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.infrastructure.BoundaryDecorators;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Receiver;
import lombok.NonNull;

import java.time.Clock;
import java.util.List;
import java.util.function.IntSupplier;

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
        var metrics = new MetricsRegistry(clock);
        var decorators = new BoundaryDecorators(metrics);

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
                clock,
                decorators);

        List<IntSupplier> pollers = List.of(
                poller(decorators, "place-account-hold", placeAccountHolds,
                        components.placeAccountHold())::pollOnce,
                poller(decorators, "account-hold-placed", placedWorkflow,
                        components.accountHoldPlaced())::pollOnce,
                poller(decorators, "account-hold-declined", declinedWorkflow,
                        components.accountHoldDeclined())::pollOnce,
                poller(decorators, "release-account-hold", releaseAccountHolds,
                        components.releaseAccountHold())::pollOnce,
                poller(decorators, "account-hold-released", releasedWorkflow,
                        components.accountHoldReleased())::pollOnce,
                poller(decorators, "journalize-transaction", journalizeTransactions,
                        components.journalizeTransaction())::pollOnce,
                poller(decorators, "transaction-journalized", journalizedWorkflow,
                        components.transactionJournalized())::pollOnce,
                poller(decorators, "settle-account-hold", settleAccountHolds,
                        components.settleAccountHold())::pollOnce,
                poller(decorators, "account-hold-settled", settledWorkflow,
                        components.accountHoldSettled())::pollOnce);

        return new InMemoryAccounting(
                accounts,
                components.transactions(),
                metrics,
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
            BoundaryDecorators decorators,
            String name,
            Receiver<T> receiver,
            Handler<T> handler) {
        return new InMemoryPoller<>(
                decorators.receiver("receive." + name, receiver),
                handler,
                BATCH_SIZE);
    }
}
