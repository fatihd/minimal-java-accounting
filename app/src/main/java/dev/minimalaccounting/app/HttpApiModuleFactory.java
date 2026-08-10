package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.api.http.HttpConfiguration;
import dev.minimalaccounting.adapters.api.http.HttpTransactionServer;
import dev.minimalaccounting.adapters.api.http.HttpTransactionServerFactory;
import dev.minimalaccounting.ports.api.SubmitTransactionHandler;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.UncheckedIOException;

@RequiredArgsConstructor
final class HttpApiModuleFactory {
    private final HttpConfiguration configuration;
    private final SubmitTransactionHandler transactions;

    @Getter(lazy = true)
    private final HttpTransactionServer transactionServer = createServer();

    private HttpTransactionServer createServer() {
        try {
            return HttpTransactionServerFactory.create(configuration, transactions);
        } catch (IOException failure) {
            throw new UncheckedIOException("create HTTP transaction server", failure);
        }
    }
}
