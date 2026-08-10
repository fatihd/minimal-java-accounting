package dev.minimalaccounting.app.adapters.fake;

import dev.minimalaccounting.adapters.api.console.ConsoleTransactionAdapter;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ConsoleApiModuleFactory {
    private final Handler<SubmitTransaction> transactions;

    @Getter(lazy = true)
    private final ConsoleTransactionAdapter transactionAdapter =
            new ConsoleTransactionAdapter(transactions);
}
