package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConnection;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisPoller;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisQueue;
import dev.minimalaccounting.adapters.messaging.artemis.ArtemisTopic;
import dev.minimalaccounting.adapters.messaging.postgresql.OutboxRoute;
import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxPublisher;
import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxSchema;
import dev.minimalaccounting.adapters.messaging.postgresql.PostgresqlOutboxSender;
import dev.minimalaccounting.adapters.messaging.postgresql.TransactionalHandler;
import dev.minimalaccounting.adapters.persistence.postgresql.JdbcTransactions;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlAccountRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlDataSourceFactory;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlJournalRepository;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlSchema;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlTransactionRepository;
import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.observability.MetricsRegistry;
import dev.minimalaccounting.observability.Observability;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Receiver;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;

import java.time.Clock;
import java.util.List;
import java.util.function.IntSupplier;

final class DurableAccountingFactory {
    private static final int BATCH_SIZE = 20;

    private DurableAccountingFactory() {
    }

    static DurableAccounting create(
            @NonNull DurableRuntimeConfiguration configuration,
            @NonNull Clock clock) {
        var jdbc = new JdbcTransactions(
                PostgresqlDataSourceFactory.create(configuration.database()));
        PostgresqlSchema.install(jdbc);
        PostgresqlOutboxSchema.install(jdbc);

        var accounts = new PostgresqlAccountRepository(jdbc);
        var transactions = new PostgresqlTransactionRepository(jdbc);
        var journals = new PostgresqlJournalRepository(jdbc);
        var broker = new ArtemisConnection(configuration.broker());

        try {
            return compose(
                    configuration.destinationPrefix(),
                    clock,
                    jdbc,
                    accounts,
                    transactions,
                    journals,
                    broker);
        } catch (RuntimeException failure) {
            broker.close();
            throw failure;
        }
    }

    private static DurableAccounting compose(
            String prefix,
            Clock clock,
            JdbcTransactions jdbc,
            PostgresqlAccountRepository accounts,
            PostgresqlTransactionRepository transactions,
            PostgresqlJournalRepository journals,
            ArtemisConnection broker) {
        String placeName = name(prefix, "command.place-account-hold");
        String releaseName = name(prefix, "command.release-account-hold");
        String journalizeName = name(prefix, "command.journalize-transaction");
        String settleName = name(prefix, "command.settle-account-hold");
        String placedName = name(prefix, "event.account-hold-placed");
        String declinedName = name(prefix, "event.account-hold-declined");
        String releasedName = name(prefix, "event.account-hold-released");
        String journalizedName = name(prefix, "event.transaction-journalized");
        String settledName = name(prefix, "event.account-hold-settled");

        var placeQueue = new ArtemisQueue<>(broker, placeName, PlaceAccountHold.class);
        var releaseQueue = new ArtemisQueue<>(broker, releaseName, ReleaseAccountHold.class);
        var journalizeQueue = new ArtemisQueue<>(
                broker, journalizeName, JournalizeTransaction.class);
        var settleQueue = new ArtemisQueue<>(broker, settleName, SettleAccountHold.class);
        var placedTopic = new ArtemisTopic<>(broker, placedName, AccountHoldPlaced.class);
        var declinedTopic = new ArtemisTopic<>(broker, declinedName, AccountHoldDeclined.class);
        var releasedTopic = new ArtemisTopic<>(broker, releasedName, AccountHoldReleased.class);
        var journalizedTopic = new ArtemisTopic<>(
                broker, journalizedName, TransactionJournalized.class);
        var settledTopic = new ArtemisTopic<>(broker, settledName, AccountHoldSettled.class);

        Receiver<AccountHoldPlaced> placedWorkflow = placedTopic.subscribe("transaction");
        Receiver<AccountHoldDeclined> declinedWorkflow =
                declinedTopic.subscribe("transaction");
        Receiver<AccountHoldReleased> releasedWorkflow =
                releasedTopic.subscribe("transaction");
        Receiver<TransactionJournalized> journalizedWorkflow =
                journalizedTopic.subscribe("transaction");
        Receiver<AccountHoldSettled> settledWorkflow =
                settledTopic.subscribe("transaction");

        AccountingSenders outboxSenders = new AccountingSenders(
                outbox(jdbc, placeName),
                outbox(jdbc, releaseName),
                outbox(jdbc, journalizeName),
                outbox(jdbc, settleName),
                outbox(jdbc, placedName),
                outbox(jdbc, declinedName),
                outbox(jdbc, releasedName),
                outbox(jdbc, journalizedName),
                outbox(jdbc, settledName));
        var metrics = new MetricsRegistry(clock);
        var observability = new Observability(metrics);
        AccountingComponents components = AccountingComponentsFactory.create(
                new AccountingRepositories(accounts, transactions, journals),
                outboxSenders,
                clock,
                observability);

        var publisher = new PostgresqlOutboxPublisher(
                jdbc,
                List.of(
                        route(placeName, PlaceAccountHold.class,
                                observed(observability, placeName, placeQueue)),
                        route(releaseName, ReleaseAccountHold.class,
                                observed(observability, releaseName, releaseQueue)),
                        route(journalizeName, JournalizeTransaction.class,
                                observed(observability, journalizeName, journalizeQueue)),
                        route(settleName, SettleAccountHold.class,
                                observed(observability, settleName, settleQueue)),
                        route(placedName, AccountHoldPlaced.class,
                                observed(observability, placedName, placedTopic)),
                        route(declinedName, AccountHoldDeclined.class,
                                observed(observability, declinedName, declinedTopic)),
                        route(releasedName, AccountHoldReleased.class,
                                observed(observability, releasedName, releasedTopic)),
                        route(journalizedName, TransactionJournalized.class,
                                observed(observability, journalizedName, journalizedTopic)),
                        route(settledName, AccountHoldSettled.class,
                                observed(observability, settledName, settledTopic))),
                BATCH_SIZE);

        List<IntSupplier> pollers = List.of(
                publisher::publishOnce,
                poller(observability, placeName, placeQueue,
                        transactional(jdbc, components.placeAccountHold())),
                poller(observability, placedName, placedWorkflow,
                        transactional(jdbc, components.accountHoldPlaced())),
                poller(observability, declinedName, declinedWorkflow,
                        transactional(jdbc, components.accountHoldDeclined())),
                poller(observability, releaseName, releaseQueue,
                        transactional(jdbc, components.releaseAccountHold())),
                poller(observability, releasedName, releasedWorkflow,
                        transactional(jdbc, components.accountHoldReleased())),
                poller(observability, journalizeName, journalizeQueue,
                        transactional(jdbc, components.journalizeTransaction())),
                poller(observability, journalizedName, journalizedWorkflow,
                        transactional(jdbc, components.transactionJournalized())),
                poller(observability, settleName, settleQueue,
                        transactional(jdbc, components.settleAccountHold())),
                poller(observability, settledName, settledWorkflow,
                        transactional(jdbc, components.accountHoldSettled())));

        return new DurableAccounting(
                accounts,
                submitted -> jdbc.inTransaction(
                        () -> components.transactions().submit(submitted)),
                metrics,
                pollers,
                broker);
    }

    private static String name(String prefix, String destination) {
        return prefix + '.' + destination;
    }

    private static <T> Sender<T> outbox(JdbcTransactions jdbc, String destination) {
        return new PostgresqlOutboxSender<>(jdbc, destination);
    }

    private static <T> OutboxRoute<T> route(
            String destination,
            Class<T> type,
            Sender<T> sender) {
        return new OutboxRoute<>(destination, type, sender);
    }

    private static <T> Sender<T> observed(
            Observability observability,
            String name,
            Sender<T> sender) {
        return observability.sender("broker." + name, sender);
    }

    private static <T> Handler<T> transactional(
            JdbcTransactions jdbc,
            Handler<T> handler) {
        return new TransactionalHandler<>(jdbc, handler);
    }

    private static <T> IntSupplier poller(
            Observability observability,
            String name,
            Receiver<T> receiver,
            Handler<T> handler) {
        var poller = new ArtemisPoller<>(
                observability.receiver("receive." + name, receiver),
                handler,
                BATCH_SIZE);
        return poller::pollOnce;
    }
}
