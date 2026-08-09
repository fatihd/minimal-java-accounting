package dev.minimalaccounting.adapters.api.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import lombok.NonNull;

import java.io.IOException;
import java.util.concurrent.Executors;

public final class HttpTransactionServerFactory {
    private HttpTransactionServerFactory() {
    }

    public static HttpTransactionServer create(
            @NonNull HttpConfiguration configuration,
            @NonNull TransactionProcessor transactions) throws IOException {
        HttpServer server = HttpServer.create(
                configuration.address(), configuration.backlog());
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext(
                "/transactions",
                new TransactionHttpHandler(
                        transactions,
                        new ObjectMapper(),
                        configuration.maximumBodyBytes()));
        return new HttpTransactionServer(server, executor);
    }
}
