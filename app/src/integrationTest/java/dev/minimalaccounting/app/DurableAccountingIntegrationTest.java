package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
import dev.minimalaccounting.adapters.api.http.HttpTransactionServer;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConfiguration;
import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlAccountRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlConfiguration;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlDataSourceFactory;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlJournalRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlTransactionRepository;
import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.core.transaction.TransactionState;
import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.Currency;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableAccountingIntegrationTest {
    private static final String DATABASE_USER = "accounting_test";
    private static final String DATABASE_PASSWORD = UUID.randomUUID().toString();
    private static final String BROKER_USER = "accounting_test";
    private static final String BROKER_PASSWORD = UUID.randomUUID().toString();
    private static final PostgreSQLContainer<?> DATABASE =
            new PostgreSQLContainer<>("postgres:17.6-alpine")
                    .withUsername(DATABASE_USER)
                    .withPassword(DATABASE_PASSWORD)
                    .withDatabaseName("accounting");
    private static final GenericContainer<?> BROKER = new GenericContainer<>(
            DockerImageName.parse("apache/artemis:2.55.0-alpine"))
            .withEnv("ARTEMIS_USER", BROKER_USER)
            .withEnv("ARTEMIS_PASSWORD", BROKER_PASSWORD)
            .withEnv("ANONYMOUS_LOGIN", "false")
            .withExposedPorts(61616)
            .waitingFor(Wait.forListeningPort());

    static {
        DATABASE.start();
        BROKER.start();
    }

    private PostgresqlConfiguration database;
    private JdbcTransactions jdbc;

    @BeforeEach
    void resetDatabase() {
        database = new PostgresqlConfiguration(
                DATABASE.getJdbcUrl(), DATABASE_USER, DATABASE_PASSWORD);
        jdbc = new JdbcTransactions(PostgresqlDataSourceFactory.create(database));
        jdbc.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA public CASCADE");
                statement.execute("CREATE SCHEMA public");
            }
            return null;
        });
    }

    @Test
    @Timeout(30)
    void resumesAnAcceptedTransactionAfterRestartAndIgnoresItsDuplicate()
            throws Exception {
        DurableRuntimeConfiguration configuration = configuration();

        ApplicationFactory firstFactory =
                ApplicationFactory.durable(configuration, Clock.systemUTC());
        try (AccountingRuntime first = firstFactory.getRuntime()) {
            addAccounts(first);
            try (HttpTransactionServer server = server(firstFactory)) {
                assertEquals(202, submit(server).statusCode());
            }
        }

        assertEquals(TransactionState.PLACING_HOLDS,
                new PostgresqlTransactionRepository(jdbc)
                        .get(TransactionReference.request("request-1"))
                        .orElseThrow()
                        .getState());
        assertEquals(2, unpublishedMessages());

        ApplicationFactory restartedFactory =
                ApplicationFactory.durable(configuration, Clock.systemUTC());
        try (AccountingRuntime restarted = restartedFactory.getRuntime();
             WorkerGroup workers = restarted.startWorkers();
             HttpTransactionServer server = server(restartedFactory)) {
            await(this::completed);

            var transactions = new PostgresqlTransactionRepository(jdbc);
            var journals = new PostgresqlJournalRepository(jdbc);
            var accounts = new PostgresqlAccountRepository(jdbc);
            long journalEntryId = journals.get(JournalReference.request("request-1"))
                    .orElseThrow().getId();
            long debitBalance = accounts.get(AccountReference.number(
                    new AccountNumber("DEBIT001"))).orElseThrow().getBalance().minor();
            long creditBalance = accounts.get(AccountReference.number(
                    new AccountNumber("CREDIT01"))).orElseThrow().getBalance().minor();

            assertEquals(202, submit(server).statusCode());
            await(this::outboxEmpty);

            assertTrue(transactions.get(TransactionReference.request("request-1"))
                    .isEmpty());
            assertEquals(journalEntryId,
                    journals.get(JournalReference.request("request-1"))
                            .orElseThrow().getId());
            assertEquals(debitBalance, accounts.get(AccountReference.number(
                    new AccountNumber("DEBIT001"))).orElseThrow().getBalance().minor());
            assertEquals(creditBalance, accounts.get(AccountReference.number(
                    new AccountNumber("CREDIT01"))).orElseThrow().getBalance().minor());
        }
    }

    private DurableRuntimeConfiguration configuration() {
        return new DurableRuntimeConfiguration(
                database,
                new ArtemisConfiguration(
                        "tcp://" + BROKER.getHost() + ':' + BROKER.getMappedPort(61616),
                        BROKER_USER,
                        BROKER_PASSWORD,
                        25),
                "integration." + UUID.randomUUID());
    }

    private static void addAccounts(AccountingRuntime accounting) {
        Currency usd = Currency.getInstance("USD");
        accounting.accounts().add(Account.create(
                new AccountNumber("DEBIT001"),
                "Debit account",
                new Money(1_000, usd),
                null,
                null));
        accounting.accounts().add(Account.create(
                new AccountNumber("CREDIT01"),
                "Credit account",
                new Money(1_000, usd),
                null,
                null));
    }

    private static HttpTransactionServer server(ApplicationFactory factory) {
        HttpConfiguration configuration = new HttpConfiguration(
                new InetSocketAddress("127.0.0.1", 0), 0, 1_000_000);
        HttpTransactionServer server = factory.httpApiModule(configuration)
                .getTransactionServer();
        server.start();
        return server;
    }

    private static HttpResponse<String> submit(HttpTransactionServer server)
            throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + server.address().getPort() + "/transactions"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "requestId": "request-1",
                          "documentType": "TRANSFER",
                          "documentNumber": "DOC-1",
                          "description": "durable integration",
                          "occurredAt": "2026-08-09T12:00:00Z",
                          "lines": [
                            {
                              "accountNumber": "DEBIT001",
                              "side": "DEBIT",
                              "transactionAmount": {"minor": 100, "currency": "USD"},
                              "ledgerAmount": {"minor": 100, "currency": "USD"}
                            },
                            {
                              "accountNumber": "CREDIT01",
                              "side": "CREDIT",
                              "transactionAmount": {"minor": 100, "currency": "USD"},
                              "ledgerAmount": {"minor": 100, "currency": "USD"}
                            }
                          ]
                        }
                        """))
                .build();
        return HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString());
    }

    private boolean completed() {
        var transaction = new PostgresqlTransactionRepository(jdbc)
                .get(TransactionReference.request("request-1"));
        var journal = new PostgresqlJournalRepository(jdbc)
                .get(JournalReference.request("request-1"));
        return transaction.isEmpty() && journal.isPresent();
    }

    private boolean outboxEmpty() {
        return jdbc.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT COUNT(*) FROM outbox_message WHERE published_at IS NULL
                         """)) {
                result.next();
                return result.getLong(1) == 0;
            }
        });
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        for (int attempt = 0; attempt < 400; attempt++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        assertTrue(
                condition.getAsBoolean(),
                () -> "durable workflow did not complete: " + diagnostics());
    }

    private long unpublishedMessages() {
        return jdbc.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT COUNT(*) FROM outbox_message WHERE published_at IS NULL
                         """)) {
                result.next();
                return result.getLong(1);
            }
        });
    }

    private String diagnostics() {
        var transaction = new PostgresqlTransactionRepository(jdbc)
                .get(TransactionReference.request("request-1"));
        boolean journal = new PostgresqlJournalRepository(jdbc)
                .get(JournalReference.request("request-1")).isPresent();
        return "transaction="
                + transaction.map(value -> value.getState() + "/" + value.getEffects())
                .orElse("missing")
                + ", journal=" + journal
                + ", unpublished=" + unpublishedMessages();
    }
}
