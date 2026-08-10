package dev.minimalaccounting.app;

import dev.minimalaccounting.app.adapters.fake.InMemoryMessagingModuleFactory;
import dev.minimalaccounting.app.adapters.fake.InMemoryPersistenceModuleFactory;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentJournalRepository;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentTransactionRepository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.Money;

import java.time.Clock;
import java.util.Currency;
import java.util.List;

final class AccountingFixture {
    final ConcurrentAccountRepository accounts;
    final ConcurrentTransactionRepository transactions;
    final ConcurrentJournalRepository journals;
    final Handler<SubmitTransaction> submitTransaction;

    private final InMemoryMessagingModuleFactory messaging;
    private final ApplicationFactory application;

    AccountingFixture(Clock clock) {
        var persistence = new InMemoryPersistenceModuleFactory();
        messaging = new InMemoryMessagingModuleFactory();
        var modules = new InMemoryModules(
                AccountingDestinations.prefixed("test"), persistence, messaging);
        var infrastructure = new InfrastructureModuleFactory(clock);
        application = new ApplicationFactory(clock, modules, infrastructure);

        accounts = persistence.getAccountRepository();
        transactions = persistence.getTransactionRepository();
        journals = persistence.getJournalRepository();
        submitTransaction = application.getTransactions();
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
                new AccountNumber(accountNumber),
                accountNumber,
                new Money(balance, currency),
                minimum == null ? null : new Money(minimum, currency),
                maximum == null ? null : new Money(maximum, currency),
                List.of()));
    }

    void processUntilIdle() {
        application.processUntilIdle();
    }

    int pendingMessages() {
        return messaging.getQueues().stream()
                .mapToInt(queue -> queue.size())
                .sum();
    }
}
