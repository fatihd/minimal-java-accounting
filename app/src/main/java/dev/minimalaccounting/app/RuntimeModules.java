package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.inmemory.InMemoryPoller;
import dev.minimalaccounting.adapters.messaging.outbox.OutboxPoller;
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.app.adapters.fake.InMemoryMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.fake.InMemoryPersistenceModuleFactory;
import dev.minimalaccounting.app.adapters.MessagingComponents;
import dev.minimalaccounting.app.adapters.PersistenceComponents;
import dev.minimalaccounting.app.adapters.real.OutboxMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.real.PostgresqlModuleFactory;
import dev.minimalaccounting.app.adapters.real.PostgresqlPersistenceModuleFactory;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

sealed interface RuntimeModules permits InMemoryModules, DurableModules {
    PersistenceComponents persistence();

    MessagingComponents messaging();

    AccountingDestinations destinations();

    <T> Handler<T> transactional(Handler<T> handler);

    String receiveName(String localName, String destination);

    <T> IntSupplier poller(Receiver<T> receiver, Handler<T> handler, int batchSize);

    List<IntSupplier> pollers(List<IntSupplier> workflowPollers);

    AccountingRuntime runtime(
            Repository<AccountIdentifier, Account> accounts,
            Handler<SubmitTransaction> transactions,
            MetricsRegistry metrics,
            List<IntSupplier> pollers);
}

record InMemoryModules(
        AccountingDestinations destinations,
        InMemoryPersistenceModuleFactory persistence,
        InMemoryMessagingModuleFactory messaging) implements RuntimeModules {

    @Override
    public <T> Handler<T> transactional(Handler<T> handler) {
        return handler;
    }

    @Override
    public String receiveName(String localName, String destination) {
        return "receive." + localName;
    }

    @Override
    public <T> IntSupplier poller(
            Receiver<T> receiver,
            Handler<T> handler,
            int batchSize) {
        var poller = new InMemoryPoller<>(receiver, handler, batchSize);
        return poller::pollOnce;
    }

    @Override
    public List<IntSupplier> pollers(List<IntSupplier> workflowPollers) {
        return workflowPollers;
    }

    @Override
    public AccountingRuntime runtime(
            Repository<AccountIdentifier, Account> accounts,
            Handler<SubmitTransaction> transactions,
            MetricsRegistry metrics,
            List<IntSupplier> pollers) {
        return new InMemoryAccounting(
                accounts, transactions, metrics, messaging.getQueues(), pollers);
    }
}

record DurableModules(
        AccountingDestinations destinations,
        PostgresqlModuleFactory postgresql,
        PostgresqlPersistenceModuleFactory persistence,
        OutboxMessagingModuleFactory messaging) implements RuntimeModules {

    @Override
    public <T> Handler<T> transactional(Handler<T> handler) {
        return message -> postgresql.getTransactions()
                .inTransaction(() -> handler.handle(message));
    }

    @Override
    public String receiveName(String localName, String destination) {
        return "receive." + destination;
    }

    @Override
    public <T> IntSupplier poller(
            Receiver<T> receiver,
            Handler<T> handler,
            int batchSize) {
        var poller = new OutboxPoller<>(receiver, handler, batchSize);
        return poller::pollOnce;
    }

    @Override
    public List<IntSupplier> pollers(List<IntSupplier> workflowPollers) {
        var pollers = new ArrayList<IntSupplier>();
        pollers.add(messaging.getPublisher()::publishOnce);
        pollers.addAll(workflowPollers);
        return List.copyOf(pollers);
    }

    @Override
    public AccountingRuntime runtime(
            Repository<AccountIdentifier, Account> accounts,
            Handler<SubmitTransaction> transactions,
            MetricsRegistry metrics,
            List<IntSupplier> pollers) {
        var broker = messaging.getConnection();
        try {
            return new DurableAccounting(accounts, transactions, metrics, pollers, broker);
        } catch (RuntimeException failure) {
            broker.close();
            throw failure;
        }
    }
}
