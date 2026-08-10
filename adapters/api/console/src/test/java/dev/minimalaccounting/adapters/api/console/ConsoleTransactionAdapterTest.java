package dev.minimalaccounting.adapters.api.console;

import dev.minimalaccounting.model.transaction.SubmitTransaction;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleTransactionAdapterTest {
    @Test
    void translatesSubmitCommandIntoTransaction() {
        AtomicReference<SubmitTransaction> submitted = new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConsoleTransactionAdapter adapter = new ConsoleTransactionAdapter(submitted::set);

        int status = adapter.run(command(), print(output), print(new ByteArrayOutputStream()));

        assertEquals(0, status);
        assertEquals("request-1", submitted.get().requestId());
        assertEquals(2, submitted.get().lines().size());
        assertEquals("accepted request-1\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void reportsInvalidSyntaxWithoutCallingApi() {
        AtomicReference<SubmitTransaction> submitted = new AtomicReference<>();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        ConsoleTransactionAdapter adapter = new ConsoleTransactionAdapter(submitted::set);

        int status = adapter.run(
                new String[]{"submit", "--request-id", "request-1"},
                print(new ByteArrayOutputStream()),
                print(error));

        assertEquals(2, status);
        assertNull(submitted.get());
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("missing --document-type"));
    }

    @Test
    void reportsApiRejection() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        ConsoleTransactionAdapter adapter = new ConsoleTransactionAdapter(transaction -> {
            throw new IllegalArgumentException("account is closed");
        });

        int status = adapter.run(command(), print(new ByteArrayOutputStream()), print(error));

        assertEquals(1, status);
        assertEquals(
                "SubmitTransaction rejected: account is closed\n",
                error.toString(StandardCharsets.UTF_8));
    }

    private static String[] command() {
        return new String[]{
                "submit",
                "--request-id", "request-1",
                "--document-type", "TRANSFER",
                "--document-number", "DOC-1",
                "--description", "demo",
                "--occurred-at", "2026-08-08T00:00:00Z",
                "--line", "DEBIT001", "DEBIT", "100", "USD", "100", "USD",
                "--line", "CREDIT01", "CREDIT", "100", "USD", "100", "USD"
        };
    }

    private static PrintStream print(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }
}
