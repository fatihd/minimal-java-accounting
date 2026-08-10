package dev.minimalaccounting.app;

import dev.minimalaccounting.app.adapters.fake.InMemoryMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.fake.InMemoryPersistenceModuleFactory;
import dev.minimalaccounting.app.adapters.real.HttpApiModuleFactory;
import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
import dev.minimalaccounting.adapters.messaging.outbox.ArtemisConfiguration;
import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryTopic;
import dev.minimalaccounting.shared.adapters.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationFactoryTest {
    @Test
    void memoizesModuleAndOverallComponents() {
        Clock clock = Clock.systemUTC();
        var persistence = new InMemoryPersistenceModuleFactory();
        var messaging = new InMemoryMessagingModuleFactory();
        var infrastructure = new InfrastructureModuleFactory(clock);
        var application = new ApplicationFactory(
                clock,
                new InMemoryModules(
                        AccountingDestinations.prefixed("test"), persistence, messaging),
                infrastructure);

        assertSame(
                persistence.getAccountRepository(),
                persistence.getAccountRepository());
        assertSame(
                messaging.getAccountHoldsPlaced(),
                messaging.getAccountHoldsPlaced());
        assertSame(
                messaging.getAccountHoldPlacedWorkflow(),
                messaging.getAccountHoldPlacedWorkflow());
        assertSame(
                infrastructure.getMetricsRegistry(),
                infrastructure.getMetricsRegistry());
        assertSame(application.getApplicationModule(), application.getApplicationModule());
        assertSame(application.getTransactions(), application.getTransactions());
        assertSame(application.getRuntime(), application.getRuntime());
        assertSame(
                application.getConsoleApiModule().getTransactionAdapter(),
                application.getConsoleApiModule().getTransactionAdapter());
    }

    @Test
    void constructsOnlyTheRequestedApplicationHandler() {
        var accounts = new InMemoryPersistenceModuleFactory().getAccountRepository();
        var placed = new InMemoryTopic<AccountHoldPlaced>();
        var declined = new InMemoryTopic<AccountHoldDeclined>();
        var application = new ApplicationModuleFactory(
                accounts,
                null,
                null,
                null,
                null,
                null,
                null,
                placed,
                declined,
                null,
                null,
                null,
                null);

        assertSame(
                application.getPlaceAccountHoldHandler(),
                application.getPlaceAccountHoldHandler());
    }

    @Test
    void durableFactoryConstructionDoesNotOpenExternalResources() {
        var configuration = new DurableRuntimeConfiguration(
                new PostgresqlConfiguration(
                        "jdbc:postgresql://127.0.0.1:1/unavailable",
                        "accounting",
                        "secret"),
                new ArtemisConfiguration(
                        "tcp://127.0.0.1:1",
                        "accounting",
                        "secret",
                        0),
                "lazy-test");

        assertDoesNotThrow(() -> ApplicationFactory.durable(
                configuration, Clock.systemUTC()));
    }

    @Test
    void httpModuleMemoizesItsServer() {
        Handler<SubmitTransaction> transactions = transaction -> { };
        var configuration = new HttpConfiguration(
                new InetSocketAddress("127.0.0.1", 0), 0, 1_000);
        var http = new HttpApiModuleFactory(configuration, transactions);

        try (var server = http.getTransactionServer()) {
            assertSame(server, http.getTransactionServer());
        }
    }
}
