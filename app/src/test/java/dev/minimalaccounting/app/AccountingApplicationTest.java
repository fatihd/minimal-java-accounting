package dev.minimalaccounting.app;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountingApplicationTest {
    @Test
    void showsConsoleHelpThroughTheExplicitMode() {
        var output = new ByteArrayOutputStream();

        int status = new AccountingApplication().run(
                new String[]{"console", "--help"},
                new PrintStream(output),
                System.err);

        assertEquals(0, status);
        assertTrue(output.toString().contains("submit"));
    }

    @Test
    void showsServerHelpWithoutStartingTheRuntime() {
        var output = new ByteArrayOutputStream();

        int status = new AccountingApplication().run(
                new String[]{"server", "--help"},
                new PrintStream(output),
                System.err);

        assertEquals(0, status);
        assertTrue(output.toString().contains("--runtime memory"));
    }
}
