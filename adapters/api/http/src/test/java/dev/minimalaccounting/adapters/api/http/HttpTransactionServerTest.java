package dev.minimalaccounting.adapters.api.http;

import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.model.PostingRejectedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransactionServerTest {
    private static final String VALID_TRANSACTION = """
            {
              "requestId": "request-1",
              "documentType": "TRANSFER",
              "documentNumber": "DOC-1",
              "description": null,
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
            """;

    private final HttpClient client = HttpClient.newHttpClient();
    private HttpTransactionServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void submitsAJsonTransactionAndReturnsAcceptedWithoutABody() throws Exception {
        var submitted = new AtomicReference<SubmitTransaction>();
        start(submitted::set, 1_000_000);

        var response = send("POST", "application/json; charset=utf-8", VALID_TRANSACTION);

        assertEquals(202, response.statusCode());
        assertEquals("", response.body());
        assertNotNull(submitted.get());
        assertEquals("request-1", submitted.get().requestId());
        assertEquals(2, submitted.get().lines().size());
        assertEquals("USD", submitted.get().lines().getFirst()
                .ledgerAmount().currency().getCurrencyCode());
    }

    @Test
    void rejectsMalformedJsonWithoutCallingTheProcessor() throws Exception {
        var submitted = new AtomicReference<SubmitTransaction>();
        start(submitted::set, 1_000_000);

        var response = send("POST", "application/json", "{");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("message"));
        assertNull(submitted.get());
    }

    @Test
    void mapsBusinessRejectionToUnprocessableContent() throws Exception {
        start(ignored -> {
            throw new PostingRejectedException("debits and credits do not balance");
        }, 1_000_000);

        var response = send("POST", "application/json", VALID_TRANSACTION);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("debits and credits do not balance"));
    }

    @Test
    void rejectsUnsupportedMediaTypeAndMethod() throws Exception {
        start(ignored -> { }, 1_000_000);

        assertEquals(415, send("POST", "text/plain", VALID_TRANSACTION).statusCode());
        assertEquals(405, send("GET", "application/json", null).statusCode());
    }

    @Test
    void rejectsBodiesOverTheConfiguredLimit() throws Exception {
        start(ignored -> { }, 10);

        var response = send("POST", "application/json", VALID_TRANSACTION);

        assertEquals(413, response.statusCode());
    }

    @Test
    void hidesUnexpectedFailureDetails() throws Exception {
        start(ignored -> {
            throw new IllegalStateException("database password leaked");
        }, 1_000_000);

        var response = send("POST", "application/json", VALID_TRANSACTION);

        assertEquals(500, response.statusCode());
        assertEquals("{\"message\":\"Internal server error\"}", response.body());
    }

    @Test
    void validatesServerConfiguration() {
        var address = new InetSocketAddress("127.0.0.1", 0);

        assertThrows(IllegalArgumentException.class,
                () -> new HttpConfiguration(address, -1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new HttpConfiguration(address, 0, 0));
    }

    private void start(
            Handler<SubmitTransaction> submitTransaction,
            int maximumBodyBytes) throws Exception {
        server = HttpTransactionServerFactory.create(
                new HttpConfiguration(
                        new InetSocketAddress("127.0.0.1", 0), 0, maximumBodyBytes),
                submitTransaction);
        server.start();
    }

    private HttpResponse<String> send(
            String method,
            String contentType,
            String body) throws Exception {
        var request = HttpRequest.newBuilder(endpoint())
                .header("Content-Type", contentType)
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.address().getPort() + "/transactions");
    }
}
