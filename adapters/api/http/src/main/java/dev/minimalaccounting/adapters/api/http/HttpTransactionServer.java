package dev.minimalaccounting.adapters.api.http;

import com.sun.net.httpserver.HttpServer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

@RequiredArgsConstructor
public final class HttpTransactionServer implements AutoCloseable {
    @NonNull
    private final HttpServer server;
    @NonNull
    private final ExecutorService executor;

    public void start() {
        server.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.close();
    }
}
