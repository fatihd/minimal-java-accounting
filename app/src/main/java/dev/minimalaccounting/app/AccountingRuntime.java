package dev.minimalaccounting.app;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;

interface AccountingRuntime extends AutoCloseable {
    Repository<AccountNumber, Account> accounts();

    Handler<SubmitTransaction> transactions();

    MetricsRegistry metrics();

    WorkerGroup startWorkers();

    @Override
    default void close() {
    }
}
