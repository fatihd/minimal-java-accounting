package dev.minimalaccounting.app;

import dev.minimalaccounting.app.adapters.fake.InMemoryMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.fake.InMemoryPersistenceModuleFactory;
import dev.minimalaccounting.app.adapters.fake.ConsoleApiModuleFactory;
import dev.minimalaccounting.app.adapters.real.HttpApiModuleFactory;
import dev.minimalaccounting.app.adapters.real.OutboxMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.real.PostgresqlModuleFactory;
import dev.minimalaccounting.app.adapters.real.PostgresqlPersistenceModuleFactory;
import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
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
            "send.place-account-hold", modules.messaging().getPlaceAccountHolds());

    @Getter(lazy = true)
    private final Sender<ReleaseAccountHold> releaseAccountHolds = getDecorators().sender(
            "send.release-account-hold", modules.messaging().getReleaseAccountHolds());

    @Getter(lazy = true)
    private final Sender<JournalizeTransaction> journalizeTransactions =
            getDecorators().sender(
                    "send.journalize-transaction",
                    modules.messaging().getJournalizeTransactions());

    @Getter(lazy = true)
    private final Sender<SettleAccountHold> settleAccountHolds = getDecorators().sender(
            "send.settle-account-hold", modules.messaging().getSettleAccountHolds());

    @Getter(lazy = true)
    private final Sender<AccountHoldPlaced> accountHoldsPlaced = getDecorators().sender(
            "publish.account-hold-placed", modules.messaging().getAccountHoldsPlaced());

    @Getter(lazy = true)
    private final Sender<AccountHoldDeclined> accountHoldsDeclined =
            getDecorators().sender(
                    "publish.account-hold-declined",
                    modules.messaging().getAccountHoldsDeclined());

    @Getter(lazy = true)
    private final Sender<AccountHoldReleased> accountHoldsReleased =
            getDecorators().sender(
                    "publish.account-hold-released",
                    modules.messaging().getAccountHoldsReleased());

    @Getter(lazy = true)
    private final Sender<TransactionJournalized> transactionsJournalized =
            getDecorators().sender(
                    "publish.transaction-journalized",
                    modules.messaging().getTransactionsJournalized());

    @Getter(lazy = true)
    private final Sender<AccountHoldSettled> accountHoldsSettled =
            getDecorators().sender(
                    "publish.account-hold-settled",
                    modules.messaging().getAccountHoldsSettled());

    @Getter(lazy = true)
    private final HandlerWrapper handlerWrapper =
            observedHandlers().andThen(modules.handlerWrapper());

    @Getter(lazy = true)
    private final ApplicationModuleFactory applicationModule =
            new ApplicationModuleFactory(
                    modules.persistence().getAccountRepository(),
                    modules.persistence().getTransactionRepository(),
                    modules.persistence().getJournalRepository(),
                    getPlaceAccountHolds(),
                    getReleaseAccountHolds(),
                    getJournalizeTransactions(),
                    getSettleAccountHolds(),
                    getAccountHoldsPlaced(),
                    getAccountHoldsDeclined(),
                    getAccountHoldsReleased(),
                    getTransactionsJournalized(),
                    getAccountHoldsSettled(),
                    clock,
                    getHandlerWrapper());

    @Getter(lazy = true)
    private final Handler<SubmitTransaction> transactions =
            getApplicationModule().getTransactionIntake();

    @Getter(lazy = true)
    private final Receiver<PlaceAccountHold> placeAccountHoldReceiver =
            getDecorators().receiver(
                    receiveName("place-account-hold", destinations().placeAccountHold()),
                    modules.messaging().getPlaceAccountHoldReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldPlaced> accountHoldPlacedReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-placed", destinations().accountHoldPlaced()),
                    modules.messaging().getAccountHoldPlacedWorkflow());

    @Getter(lazy = true)
    private final Receiver<AccountHoldDeclined> accountHoldDeclinedReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-declined", destinations().accountHoldDeclined()),
                    modules.messaging().getAccountHoldDeclinedWorkflow());

    @Getter(lazy = true)
    private final Receiver<ReleaseAccountHold> releaseAccountHoldReceiver =
            getDecorators().receiver(
                    receiveName("release-account-hold", destinations().releaseAccountHold()),
                    modules.messaging().getReleaseAccountHoldReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldReleased> accountHoldReleasedReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-released", destinations().accountHoldReleased()),
                    modules.messaging().getAccountHoldReleasedWorkflow());

    @Getter(lazy = true)
    private final Receiver<JournalizeTransaction> journalizeTransactionReceiver =
            getDecorators().receiver(
                    receiveName(
                            "journalize-transaction",
                            destinations().journalizeTransaction()),
                    modules.messaging().getJournalizeTransactionReceiver());

    @Getter(lazy = true)
    private final Receiver<TransactionJournalized> transactionJournalizedReceiver =
            getDecorators().receiver(
                    receiveName(
                            "transaction-journalized",
                            destinations().transactionJournalized()),
                    modules.messaging().getTransactionJournalizedWorkflow());

    @Getter(lazy = true)
    private final Receiver<SettleAccountHold> settleAccountHoldReceiver =
            getDecorators().receiver(
                    receiveName("settle-account-hold", destinations().settleAccountHold()),
                    modules.messaging().getSettleAccountHoldReceiver());

    @Getter(lazy = true)
    private final Receiver<AccountHoldSettled> accountHoldSettledReceiver =
            getDecorators().receiver(
                    receiveName("account-hold-settled", destinations().accountHoldSettled()),
                    modules.messaging().getAccountHoldSettledWorkflow());

    @Getter(lazy = true)
    private final IntSupplier placeAccountHoldPoller = poller(
            getPlaceAccountHoldReceiver(),
            getApplicationModule().getPlaceAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldPlacedPoller = poller(
            getAccountHoldPlacedReceiver(),
            getApplicationModule().getAccountHoldPlacedHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldDeclinedPoller = poller(
            getAccountHoldDeclinedReceiver(),
            getApplicationModule().getAccountHoldDeclinedHandler());

    @Getter(lazy = true)
    private final IntSupplier releaseAccountHoldPoller = poller(
            getReleaseAccountHoldReceiver(),
            getApplicationModule().getReleaseAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldReleasedPoller = poller(
            getAccountHoldReleasedReceiver(),
            getApplicationModule().getAccountHoldReleasedHandler());

    @Getter(lazy = true)
    private final IntSupplier journalizeTransactionPoller = poller(
            getJournalizeTransactionReceiver(),
            getApplicationModule().getJournalizeTransactionHandler());

    @Getter(lazy = true)
    private final IntSupplier transactionJournalizedPoller = poller(
            getTransactionJournalizedReceiver(),
            getApplicationModule().getTransactionJournalizedHandler());

    @Getter(lazy = true)
    private final IntSupplier settleAccountHoldPoller = poller(
            getSettleAccountHoldReceiver(),
            getApplicationModule().getSettleAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldSettledPoller = poller(
            getAccountHoldSettledReceiver(),
            getApplicationModule().getAccountHoldSettledHandler());

    @Getter(lazy = true)
    private final List<IntSupplier> pollers = createPollers();

    @Getter(lazy = true)
    private final AccountingRuntime runtime = createRuntime();

    @Getter(lazy = true)
    private final ConsoleApiModuleFactory consoleApiModule =
            new ConsoleApiModuleFactory(getTransactions());

    static ApplicationFactory inMemory(Clock clock) {
        var destinations = AccountingDestinations.prefixed("memory");
        var persistence = new InMemoryPersistenceModuleFactory();
        var messaging = new InMemoryMessagingModuleFactory();
        var runtimeModules = new InMemoryModules(destinations, persistence, messaging);
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
        var messaging = new OutboxMessagingModuleFactory(
                postgresql, configuration.broker(), destinations, BATCH_SIZE);
        var runtimeModules = new DurableModules(
                destinations, postgresql, persistence, messaging);
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

    private HandlerWrapper observedHandlers() {
        return new HandlerWrapper() {
            @Override
            public <T> Handler<T> apply(Handler<T> handler) {
                return getDecorators().handler(
                        "handler." + handler.getClass().getSimpleName(), handler);
            }
        };
    }

    private String receiveName(String memoryName, String durableName) {
        return modules.receiveName(memoryName, durableName);
    }

    private AccountingDestinations destinations() {
        return modules.destinations();
    }

    private <T> IntSupplier poller(Receiver<T> receiver, Handler<T> handler) {
        return modules.poller(receiver, handler, BATCH_SIZE);
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
        return modules.pollers(workflowPollers);
    }

    private AccountingRuntime createRuntime() {
        return modules.runtime(
                modules.persistence().getAccountRepository(),
                getTransactions(),
                infrastructure.getMetricsRegistry(),
                getPollers());
    }
}
