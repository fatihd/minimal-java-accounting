package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.api.console.ConsoleTransactionAdapter;
import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
final class ConsoleApiModuleFactory {
    private final SubmitTransactionHandler transactions;

    @Getter(lazy = true)
    private final ConsoleTransactionAdapter transactionAdapter =
            new ConsoleTransactionAdapter(transactions);
}
