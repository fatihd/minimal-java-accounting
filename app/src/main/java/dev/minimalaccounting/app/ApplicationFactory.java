package dev.minimalaccounting.app;

import dev.minimalaccounting.app.adapters.fake.InMemoryMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.fake.InMemoryPersistenceModuleFactory;
import dev.minimalaccounting.app.adapters.fake.ConsoleApiModuleFactory;
import dev.minimalaccounting.app.adapters.real.HttpApiModuleFactory;
import dev.minimalaccounting.app.adapters.real.OutboxMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.real.PostgresqlModuleFactory;
import dev.minimalaccounting.app.adapters.real.PostgresqlPersistenceModuleFactory;
import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Receiver;

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
    private final HandlerWrapper handlerWrapper =
            observedHandlers().andThen(modules.handlerWrapper());

    @Getter(lazy = true)
    private final ApplicationModuleFactory applicationModule =
            new ApplicationModuleFactory(
                    modules.persistence().getAccountRepository(),
                    modules.persistence().getTransactionRepository(),
                    modules.persistence().getJournalRepository(),
                    modules.messaging().getPlaceAccountHolds(),
                    modules.messaging().getReleaseAccountHolds(),
                    modules.messaging().getJournalizeTransactions(),
                    modules.messaging().getSettleAccountHolds(),
                    modules.messaging().getAccountHoldsPlaced(),
                    modules.messaging().getAccountHoldsDeclined(),
                    modules.messaging().getAccountHoldsReleased(),
                    modules.messaging().getTransactionsJournalized(),
                    modules.messaging().getAccountHoldsSettled(),
                    clock,
                    getHandlerWrapper());

    @Getter(lazy = true)
    private final Handler<SubmitTransaction> transactions =
            getApplicationModule().getTransactionIntake();

    @Getter(lazy = true)
    private final IntSupplier placeAccountHoldPoller = poller(
            modules.messaging().getPlaceAccountHoldReceiver(),
            getApplicationModule().getPlaceAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldPlacedPoller = poller(
            modules.messaging().getAccountHoldPlacedWorkflow(),
            getApplicationModule().getAccountHoldPlacedHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldDeclinedPoller = poller(
            modules.messaging().getAccountHoldDeclinedWorkflow(),
            getApplicationModule().getAccountHoldDeclinedHandler());

    @Getter(lazy = true)
    private final IntSupplier releaseAccountHoldPoller = poller(
            modules.messaging().getReleaseAccountHoldReceiver(),
            getApplicationModule().getReleaseAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldReleasedPoller = poller(
            modules.messaging().getAccountHoldReleasedWorkflow(),
            getApplicationModule().getAccountHoldReleasedHandler());

    @Getter(lazy = true)
    private final IntSupplier journalizeTransactionPoller = poller(
            modules.messaging().getJournalizeTransactionReceiver(),
            getApplicationModule().getJournalizeTransactionHandler());

    @Getter(lazy = true)
    private final IntSupplier transactionJournalizedPoller = poller(
            modules.messaging().getTransactionJournalizedWorkflow(),
            getApplicationModule().getTransactionJournalizedHandler());

    @Getter(lazy = true)
    private final IntSupplier settleAccountHoldPoller = poller(
            modules.messaging().getSettleAccountHoldReceiver(),
            getApplicationModule().getSettleAccountHoldHandler());

    @Getter(lazy = true)
    private final IntSupplier accountHoldSettledPoller = poller(
            modules.messaging().getAccountHoldSettledWorkflow(),
            getApplicationModule().getAccountHoldSettledHandler());

    @Getter(lazy = true)
    private final List<IntSupplier> pollers = createPollers();

    @Getter(lazy = true)
    private final AccountingRuntime runtime = createRuntime();

    @Getter(lazy = true)
    private final ConsoleApiModuleFactory consoleApiModule =
            new ConsoleApiModuleFactory(getTransactions());

    static ApplicationFactory inMemory(Clock clock) {
        var infrastructure = new InfrastructureModuleFactory(clock);
        var persistence = new InMemoryPersistenceModuleFactory();
        var messaging = new InMemoryMessagingModuleFactory(
                infrastructure.getSenderWrapper(),
                infrastructure.getReceiverWrapper());
        var runtimeModules = new InMemoryModules(persistence, messaging);
        return new ApplicationFactory(clock, runtimeModules, infrastructure);
    }

    static ApplicationFactory durable(
            DurableRuntimeConfiguration configuration,
            Clock clock) {
        AccountingDestinations destinations =
                AccountingDestinations.prefixed(configuration.destinationPrefix());
        var infrastructure = new InfrastructureModuleFactory(clock);
        var postgresql = new PostgresqlModuleFactory(configuration.database());
        var persistence = new PostgresqlPersistenceModuleFactory(postgresql);
        var messaging = new OutboxMessagingModuleFactory(
                postgresql,
                configuration.broker(),
                destinations,
                BATCH_SIZE,
                infrastructure.getSenderWrapper(),
                infrastructure.getReceiverWrapper());
        var runtimeModules = new DurableModules(
                postgresql, persistence, messaging);
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
                return infrastructure.getBoundaryDecorators().handler(
                        "handler." + handler.getClass().getSimpleName(), handler);
            }
        };
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
