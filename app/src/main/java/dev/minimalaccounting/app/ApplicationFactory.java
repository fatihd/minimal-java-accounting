package dev.minimalaccounting.app;

import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisPoller;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.postgresql.OutboxRoute;
import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxPublisher;
import dev.minimalaccounting.adapters.messaging.postgresql.TransactionalHandler;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountHoldSettled;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.model.account.SettleAccountHold;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.infrastructure.BoundaryDecorators;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.List;
import java.util.function.IntSupplier;

@RequiredArgsConstructor
final class ApplicationFactory {
    private static final int BATCH_SIZE = 20;

    private final Clock clock;
    private final RuntimeModules modules;
    private final InfrastructureModuleFactory infrastructure;

    @Getter(lazy = true)
    private final BoundaryDecorators decorators =
            new BoundaryDecorators(infrastructure.getMetricsRegistry());

    @Getter(lazy = true)
    private final Sender<PlaceAccountHold> placeAccountHolds = getDecorators().sender(
            "send.place-account-hold", rawPlaceAccountHolds());

    @Getter(lazy = true)
    private final Sender<ReleaseAccountHold> releaseAccountHolds = getDecorators().sender(
            "send.release-account-hold", rawReleaseAccountHolds());

    @Getter(lazy = true)
    private final Sender<JournalizeTransaction> journalizeTransactions =
            getDecorators().sender(
                    "send.journalize-transaction", rawJournalizeTransactions());

    @Getter(lazy = true)
    private final Sender<SettleAccountHold> settleAccountHolds = getDecorators().sender(
            "send.settle-account-hold", rawSettleAccountHolds());

    @Getter(lazy = true)
    private final Sender<AccountHoldPlaced> accountHoldsPlaced = getDecorators().sender(
            "publish.account-hold-placed", rawAccountHoldsPlaced());

    @Getter(lazy = true)
    private final Sender<AccountHoldDeclined> accountHoldsDeclined =
            getDecorators().sender(
                    "publish.account-hold-declined", rawAccountHoldsDeclined());

    @Getter(lazy = true)
    private final Sender<AccountHoldReleased> accountHoldsReleased =
            getDecorators().sender(
                    "publish.account-hold-released", rawAccountHoldsReleased());

    @Getter(lazy = true)
    private final Sender<TransactionJournalized> transactionsJournalized =
            getDecorators().sender(
                    "publish.transaction-journalized", rawTransactionsJournalized());

    @Getter(lazy = true)
    private final Sender<AccountHoldSettled> accountHoldsSettled =
            getDecorators().sender(
                    "publish.account-hold-settled", rawAccountHoldsSettled());

    @Getter(lazy = true)
    private final ApplicationModuleFactory applicationModule =
            new ApplicationModuleFactory(
                    accountRepository(),
                    transactionRepository(),
                    journalRepository(),
                    getPlaceAccountHolds(),
                    getReleaseAccountHolds(),
                    getJournalizeTransactions(),
                    getSettleAccountHolds(),
                    getAccountHoldsPlaced(),
                    getAccountHoldsDeclined(),
                    getAccountHoldsReleased(),
                    getTransactionsJournalized(),
                    getAccountHoldsSettled(),
                    clock);

    @Getter(lazy = true)
    private final Handler<SubmitTransaction> observedTransactions = getDecorators().handler(
            "transaction.submit", getApplicationModule().getTransactionIntake());

    @Getter(lazy = true)
    private final Handler<PlaceAccountHold> observedPlaceAccountHold =
            getDecorators().handler(
                    "command.place-account-hold",
                    getApplicationModule().getPlaceAccountHoldHandler());

    @Getter(lazy = true)
    private final Handler<AccountHoldPlaced> observedAccountHoldPlaced =
            getDecorators().handler(
                    "event.account-hold-placed",
                    getApplicationModule().getAccountHoldPlacedHandler());

    @Getter(lazy = true)
    private final Handler<AccountHoldDeclined> observedAccountHoldDeclined =
            getDecorators().handler(
                    "event.account-hold-declined",
                    getApplicationModule().getAccountHoldDeclinedHandler());

    @Getter(lazy = true)
    private final Handler<ReleaseAccountHold> observedReleaseAccountHold =
            getDecorators().handler(
                    "command.release-account-hold",
                    getApplicationModule().getReleaseAccountHoldHandler());

    @Getter(lazy = true)
    private final Handler<AccountHoldReleased> observedAccountHoldReleased =
            getDecorators().handler(
                    "event.account-hold-released",
                    getApplicationModule().getAccountHoldReleasedHandler());

    @Getter(lazy = true)
    private final Handler<JournalizeTransaction> observedJournalizeTransaction =
            getDecorators().handler(
                    "command.journalize-transaction",
                    getApplicationModule().getJournalizeTransactionHandler());

    @Getter(lazy = true)
    private final Handler<TransactionJournalized> observedTransactionJournalized =
            getDecorators().handler(
                    "event.transaction-journalized",
                    getApplicationModule().getTransactionJournalizedHandler());

    @Getter(lazy = true)
    private final Handler<SettleAccountHold> observedSettleAccountHold =
            getDecorators().handler(
                    "command.settle-account-hold",
                    getApplicationModule().getSettleAccountHoldHandler());

    @Getter(lazy = true)
    private final Handler<AccountHoldSettled> observedAccountHoldSettled =
            getDecorators().handler(
                    "event.account-hold-settled",
                    getApplicationModule().getAccountHoldSettledHandler());

    @Getter(lazy = true)
    private final Handler<SubmitTransaction> transactions =
            transactional(getObservedTransactions());

    @Getter(lazy = true)
    private final Handler<PlaceAccountHold> placeAccountHoldHandler =
            transactional(getObservedPlaceAccountHold());

    @Getter(lazy = true)
    private final Handler<AccountHoldPlaced> accountHoldPlacedHandler =
            transactional(getObservedAccountHoldPlaced());

    @Getter(lazy = true)
    private final Handler<AccountHoldDeclined> accountHoldDeclinedHandler =
            transactional(getObservedAccountHoldDeclined());

    @Getter(lazy = true)
    private final Handler<ReleaseAccountHold> releaseAccountHoldHandler =
            transactional(getObservedReleaseAccountHold());

    @Getter(lazy = true)
    private final Handler<AccountHoldReleased> accountHoldReleasedHandler =
            transactional(getObservedAccountHoldReleased());

    @Getter(lazy = true)
    private final Handler<JournalizeTransaction> journalizeTransactionHandler =
            transactional(getObservedJournalizeTransaction());

    @Getter(lazy = true)
    private final Handler<TransactionJournalized> transactionJournalizedHandler =
            transactional(getObservedTransactionJournalized());

    @Getter(lazy = true)
    private final Handler<SettleAccountHold> settleAccountHoldHandler =
            transactional(getObservedSettleAccountHold());

    @Getter(lazy = true)
    private final Handler<AccountHoldSettled> accountHoldSettledHandler =
            transactional(getObservedAccountHoldSettled());

    @Getter(lazy = true)
    private final Receiver<PlaceAccountHold> placeAccountHoldReceiver =
            getDecorators().receiver(
                    receiveName("place-account-hold", destinations().placeAccountHold()),
                    rawPlaceAccountHoldReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldPlaced> accountHoldPlacedReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-placed", destinations().accountHoldPlaced()),
                    rawAccountHoldPlacedReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldDeclined> accountHoldDeclinedReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-declined", destinations().accountHoldDeclined()),
                    rawAccountHoldDeclinedReceiver());

    @Getter(lazy = true)
    private final Receiver<ReleaseAccountHold> releaseAccountHoldReceiver =
            getDecorators().receiver(
                    receiveName("release-account-hold", destinations().releaseAccountHold()),
                    rawReleaseAccountHoldReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldReleased> accountHoldReleasedReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-released", destinations().accountHoldReleased()),
                    rawAccountHoldReleasedReceiver());

    @Getter(lazy = true)
    private final Receiver<JournalizeTransaction> journalizeTransactionReceiver =
            getDecorators().receiver(
                    receiveName(
                            "journalize-transaction",
                            destinations().journalizeTransaction()),
                    rawJournalizeTransactionReceiver());

    @Getter(lazy = true)
    private final Receiver<TransactionJournalized> transactionJournalizedReceiver =
            getDecorators().receiver(
                    receiveName(
                            "transaction-journalized",
                            destinations().transactionJournalized()),
                    rawTransactionJournalizedReceiver());

    @Getter(lazy = true)
    private final Receiver<SettleAccountHold> settleAccountHoldReceiver =
            getDecorators().receiver(
                    receiveName("settle-account-hold", destinations().settleAccountHold()),
                    rawSettleAccountHoldReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldSettled> accountHoldSettledReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-settled", destinations().accountHoldSettled()),
                    rawAccountHoldSettledReceiver());

    @Getter(lazy = true)
    private final Sender<PlaceAccountHold> brokerPlaceAccountHolds = brokerSender(
            destinations().placeAccountHold(), durable().broker().getPlaceAccountHolds());

    @Getter(lazy = true)
    private final Sender<ReleaseAccountHold> brokerReleaseAccountHolds = brokerSender(
            destinations().releaseAccountHold(), durable().broker().getReleaseAccountHolds());

    @Getter(lazy = true)
    private final Sender<JournalizeTransaction> brokerJournalizeTransactions = brokerSender(
            destinations().journalizeTransaction(),
            durable().broker().getJournalizeTransactions());

    @Getter(lazy = true)
    private final Sender<SettleAccountHold> brokerSettleAccountHolds = brokerSender(
            destinations().settleAccountHold(), durable().broker().getSettleAccountHolds());

    @Getter(lazy = true)
    private final Sender<AccountHoldPlaced> brokerAccountHoldsPlaced = brokerSender(
            destinations().accountHoldPlaced(), durable().broker().getAccountHoldsPlaced());

    @Getter(lazy = true)
    private final Sender<AccountHoldDeclined> brokerAccountHoldsDeclined = brokerSender(
            destinations().accountHoldDeclined(),
            durable().broker().getAccountHoldsDeclined());

    @Getter(lazy = true)
    private final Sender<AccountHoldReleased> brokerAccountHoldsReleased = brokerSender(
            destinations().accountHoldReleased(),
            durable().broker().getAccountHoldsReleased());

    @Getter(lazy = true)
    private final Sender<TransactionJournalized> brokerTransactionsJournalized =
            brokerSender(
                    destinations().transactionJournalized(),
                    durable().broker().getTransactionsJournalized());

    @Getter(lazy = true)
    private final Sender<AccountHoldSettled> brokerAccountHoldsSettled = brokerSender(
            destinations().accountHoldSettled(),
            durable().broker().getAccountHoldsSettled());

    @Getter(lazy = true)
    private final OutboxRoute<PlaceAccountHold> placeAccountHoldRoute = route(
            destinations().placeAccountHold(),
            PlaceAccountHold.class,
            getBrokerPlaceAccountHolds());

    @Getter(lazy = true)
    private final OutboxRoute<ReleaseAccountHold> releaseAccountHoldRoute = route(
            destinations().releaseAccountHold(),
            ReleaseAccountHold.class,
            getBrokerReleaseAccountHolds());

    @Getter(lazy = true)
    private final OutboxRoute<JournalizeTransaction> journalizeTransactionRoute = route(
            destinations().journalizeTransaction(),
            JournalizeTransaction.class,
            getBrokerJournalizeTransactions());

    @Getter(lazy = true)
    private final OutboxRoute<SettleAccountHold> settleAccountHoldRoute = route(
            destinations().settleAccountHold(),
            SettleAccountHold.class,
            getBrokerSettleAccountHolds());

    @Getter(lazy = true)
    private final OutboxRoute<AccountHoldPlaced> accountHoldPlacedRoute = route(
            destinations().accountHoldPlaced(),
            AccountHoldPlaced.class,
            getBrokerAccountHoldsPlaced());

    @Getter(lazy = true)
    private final OutboxRoute<AccountHoldDeclined> accountHoldDeclinedRoute = route(
            destinations().accountHoldDeclined(),
            AccountHoldDeclined.class,
            getBrokerAccountHoldsDeclined());

    @Getter(lazy = true)
    private final OutboxRoute<AccountHoldReleased> accountHoldReleasedRoute = route(
            destinations().accountHoldReleased(),
            AccountHoldReleased.class,
            getBrokerAccountHoldsReleased());

    @Getter(lazy = true)
    private final OutboxRoute<TransactionJournalized> transactionJournalizedRoute = route(
            destinations().transactionJournalized(),
            TransactionJournalized.class,
            getBrokerTransactionsJournalized());

    @Getter(lazy = true)
    private final OutboxRoute<AccountHoldSettled> accountHoldSettledRoute = route(
            destinations().accountHoldSettled(),
            AccountHoldSettled.class,
            getBrokerAccountHoldsSettled());

    @Getter(lazy = true)
    private final List<OutboxRoute<?>> outboxRoutes = List.of(
            getPlaceAccountHoldRoute(),
            getReleaseAccountHoldRoute(),
            getJournalizeTransactionRoute(),
            getSettleAccountHoldRoute(),
            getAccountHoldPlacedRoute(),
            getAccountHoldDeclinedRoute(),
            getAccountHoldReleasedRoute(),
            getTransactionJournalizedRoute(),
            getAccountHoldSettledRoute());

    @Getter(lazy = true)
    private final PostgresqlOutboxPublisher outboxPublisher =
            new PostgresqlOutboxPublisher(
                    durable().outbox().getOutboxDatabase(),
                    getOutboxRoutes(),
                    BATCH_SIZE);

    @Getter(lazy = true)
    private final IntSupplier placeAccountHoldPoller = poller(
            getPlaceAccountHoldReceiver(), getPlaceAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldPlacedPoller = poller(
            getAccountHoldPlacedReceiver(), getAccountHoldPlacedHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldDeclinedPoller = poller(
            getAccountHoldDeclinedReceiver(), getAccountHoldDeclinedHandler());

    @Getter(lazy = true)
    private final IntSupplier releaseAccountHoldPoller = poller(
            getReleaseAccountHoldReceiver(), getReleaseAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldReleasedPoller = poller(
            getAccountHoldReleasedReceiver(), getAccountHoldReleasedHandler());

    @Getter(lazy = true)
    private final IntSupplier journalizeTransactionPoller = poller(
            getJournalizeTransactionReceiver(), getJournalizeTransactionHandler());

    @Getter(lazy = true)
    private final IntSupplier transactionJournalizedPoller = poller(
            getTransactionJournalizedReceiver(), getTransactionJournalizedHandler());

    @Getter(lazy = true)
    private final IntSupplier settleAccountHoldPoller = poller(
            getSettleAccountHoldReceiver(), getSettleAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldSettledPoller = poller(
            getAccountHoldSettledReceiver(), getAccountHoldSettledHandler());

    @Getter(lazy = true)
    private final List<IntSupplier> pollers = createPollers();

    @Getter(lazy = true)
    private final AccountingRuntime runtime = createRuntime();

    @Getter(lazy = true)
    private final ConsoleApiModuleFactory consoleApiModule =
            new ConsoleApiModuleFactory(getTransactions());

    static ApplicationFactory inMemory(Clock clock) {
        var persistence = new InMemoryPersistenceModuleFactory();
        var messaging = new InMemoryMessagingModuleFactory();
        var runtimeModules = new InMemoryModules(persistence, messaging);
        var infrastructure = new InfrastructureModuleFactory(clock);
        return new ApplicationFactory(clock, runtimeModules, infrastructure);
    }

    static ApplicationFactory durable(
            DurableRuntimeConfiguration configuration,
            Clock clock) {
        AccountingDestinations destinations =
                AccountingDestinations.prefixed(configuration.destinationPrefix());
        var postgresql = new PostgresqlModuleFactory(configuration.database());
        var persistence = new PostgresqlPersistenceModuleFactory(postgresql);
        var outbox = new PostgresqlMessagingModuleFactory(postgresql, destinations);
        var broker = new ArtemisMessagingModuleFactory(
                configuration.broker(), destinations);
        var runtimeModules = new DurableModules(
                destinations, postgresql, persistence, outbox, broker);
        var infrastructure = new InfrastructureModuleFactory(clock);
        return new ApplicationFactory(clock, runtimeModules, infrastructure);
    }

    HttpApiModuleFactory httpApiModule(HttpConfiguration configuration) {
        return new HttpApiModuleFactory(configuration, getTransactions());
    }

    void processUntilIdle() {
        if (getRuntime() instanceof InMemoryAccounting accounting) {
            accounting.processUntilIdle();
            return;
        }
        throw new IllegalStateException("only in-memory messaging can be drained");
    }

    private Repository<AccountIdentifier, Account> accountRepository() {
        return switch (modules) {
            case InMemoryModules memory -> memory.persistence().getAccountRepository();
            case DurableModules durable -> durable.persistence().getAccountRepository();
        };
    }

    private Repository<TransactionIdentifier, Transaction> transactionRepository() {
        return switch (modules) {
            case InMemoryModules memory -> memory.persistence().getTransactionRepository();
            case DurableModules durable -> durable.persistence().getTransactionRepository();
        };
    }

    private Repository<JournalIdentifier, JournalEntry> journalRepository() {
        return switch (modules) {
            case InMemoryModules memory -> memory.persistence().getJournalRepository();
            case DurableModules durable -> durable.persistence().getJournalRepository();
        };
    }

    private Sender<PlaceAccountHold> rawPlaceAccountHolds() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getPlaceAccountHolds();
            case DurableModules durable -> durable.outbox().getPlaceAccountHolds();
        };
    }

    private Sender<ReleaseAccountHold> rawReleaseAccountHolds() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getReleaseAccountHolds();
            case DurableModules durable -> durable.outbox().getReleaseAccountHolds();
        };
    }

    private Sender<JournalizeTransaction> rawJournalizeTransactions() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getJournalizeTransactions();
            case DurableModules durable -> durable.outbox().getJournalizeTransactions();
        };
    }

    private Sender<SettleAccountHold> rawSettleAccountHolds() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getSettleAccountHolds();
            case DurableModules durable -> durable.outbox().getSettleAccountHolds();
        };
    }

    private Sender<AccountHoldPlaced> rawAccountHoldsPlaced() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldsPlaced();
            case DurableModules durable -> durable.outbox().getAccountHoldsPlaced();
        };
    }

    private Sender<AccountHoldDeclined> rawAccountHoldsDeclined() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldsDeclined();
            case DurableModules durable -> durable.outbox().getAccountHoldsDeclined();
        };
    }

    private Sender<AccountHoldReleased> rawAccountHoldsReleased() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldsReleased();
            case DurableModules durable -> durable.outbox().getAccountHoldsReleased();
        };
    }

    private Sender<TransactionJournalized> rawTransactionsJournalized() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getTransactionsJournalized();
            case DurableModules durable -> durable.outbox().getTransactionsJournalized();
        };
    }

    private Sender<AccountHoldSettled> rawAccountHoldsSettled() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldsSettled();
            case DurableModules durable -> durable.outbox().getAccountHoldsSettled();
        };
    }

    private Receiver<PlaceAccountHold> rawPlaceAccountHoldReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getPlaceAccountHolds();
            case DurableModules durable -> durable.broker().getPlaceAccountHolds();
        };
    }

    private Receiver<AccountHoldPlaced> rawAccountHoldPlacedReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldPlacedWorkflow();
            case DurableModules durable -> durable.broker().getAccountHoldPlacedWorkflow();
        };
    }

    private Receiver<AccountHoldDeclined> rawAccountHoldDeclinedReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldDeclinedWorkflow();
            case DurableModules durable -> durable.broker().getAccountHoldDeclinedWorkflow();
        };
    }

    private Receiver<ReleaseAccountHold> rawReleaseAccountHoldReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getReleaseAccountHolds();
            case DurableModules durable -> durable.broker().getReleaseAccountHolds();
        };
    }

    private Receiver<AccountHoldReleased> rawAccountHoldReleasedReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldReleasedWorkflow();
            case DurableModules durable -> durable.broker().getAccountHoldReleasedWorkflow();
        };
    }

    private Receiver<JournalizeTransaction> rawJournalizeTransactionReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getJournalizeTransactions();
            case DurableModules durable -> durable.broker().getJournalizeTransactions();
        };
    }

    private Receiver<TransactionJournalized> rawTransactionJournalizedReceiver() {
        return switch (modules) {
            case InMemoryModules memory ->
                    memory.messaging().getTransactionJournalizedWorkflow();
            case DurableModules durable ->
                    durable.broker().getTransactionJournalizedWorkflow();
        };
    }

    private Receiver<SettleAccountHold> rawSettleAccountHoldReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getSettleAccountHolds();
            case DurableModules durable -> durable.broker().getSettleAccountHolds();
        };
    }

    private Receiver<AccountHoldSettled> rawAccountHoldSettledReceiver() {
        return switch (modules) {
            case InMemoryModules memory -> memory.messaging().getAccountHoldSettledWorkflow();
            case DurableModules durable -> durable.broker().getAccountHoldSettledWorkflow();
        };
    }

    private <T> Handler<T> transactional(Handler<T> handler) {
        if (modules instanceof DurableModules durable) {
            return new TransactionalHandler<>(
                    durable.postgresql().getTransactions(), handler);
        }
        return handler;
    }

    private String receiveName(String memoryName, String durableName) {
        String name = modules instanceof DurableModules ? durableName : memoryName;
        return "receive." + name;
    }

    private AccountingDestinations destinations() {
        if (modules instanceof DurableModules durable) {
            return durable.destinations();
        }
        return AccountingDestinations.prefixed("memory");
    }

    private DurableModules durable() {
        if (modules instanceof DurableModules durable) {
            return durable;
        }
        throw new IllegalStateException("durable component requested from memory runtime");
    }

    private <T> Sender<T> brokerSender(String destination, Sender<T> sender) {
        return getDecorators().sender("broker." + destination, sender);
    }

    private static <T> OutboxRoute<T> route(
            String destination,
            Class<T> type,
            Sender<T> sender) {
        return new OutboxRoute<>(destination, type, sender);
    }

    private <T> IntSupplier poller(Receiver<T> receiver, Handler<T> handler) {
        if (modules instanceof InMemoryModules) {
            var poller = new InMemoryPoller<>(receiver, handler, BATCH_SIZE);
            return poller::pollOnce;
        }
        var poller = new ArtemisPoller<>(receiver, handler, BATCH_SIZE);
        return poller::pollOnce;
    }

    private List<IntSupplier> createPollers() {
        List<IntSupplier> workflowPollers = List.of(
                getPlaceAccountHoldPoller(),
                getAccountHoldPlacedPoller(),
                getAccountHoldDeclinedPoller(),
                getReleaseAccountHoldPoller(),
                getAccountHoldReleasedPoller(),
                getJournalizeTransactionPoller(),
                getTransactionJournalizedPoller(),
                getSettleAccountHoldPoller(),
                getAccountHoldSettledPoller());
        if (modules instanceof InMemoryModules) {
            return workflowPollers;
        }
        var pollers = new java.util.ArrayList<IntSupplier>();
        pollers.add(getOutboxPublisher()::publishOnce);
        pollers.addAll(workflowPollers);
        return List.copyOf(pollers);
    }

    private AccountingRuntime createRuntime() {
        if (modules instanceof InMemoryModules memory) {
            return new InMemoryAccounting(
                    accountRepository(),
                    getTransactions(),
                    infrastructure.getMetricsRegistry(),
                    memory.messaging().getQueues(),
                    getPollers());
        }

        DurableModules durable = durable();
        var broker = durable.broker().getConnection();
        try {
            return new DurableAccounting(
                    accountRepository(),
                    getTransactions(),
                    infrastructure.getMetricsRegistry(),
                    getPollers(),
                    broker);
        } catch (RuntimeException failure) {
            broker.close();
            throw failure;
        }
    }
}
