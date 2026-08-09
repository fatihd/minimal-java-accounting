package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.api.console.ConsoleTransactionAdapter;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

final class AccountingApplication {
    int run(String[] arguments, PrintStream out, PrintStream error) {
        Arguments parsed;
        try {
            parsed = Arguments.parse(arguments);
        } catch (IllegalArgumentException invalid) {
            error.println("Invalid application configuration: " + invalid.getMessage());
            printUsage(error);
            return 2;
        }

        if (parsed.help()) {
            printUsage(out);
            return 0;
        }
        if (parsed.accounts() == null) {
            error.println("Invalid application configuration: missing --accounts <file>");
            printUsage(error);
            return 2;
        }

        InMemoryAccounting accounting = new InMemoryAccounting(Clock.systemUTC());
        try {
            AccountFile.load(parsed.accounts(), accounting.accounts());
        } catch (IOException | RuntimeException failure) {
            error.println("Cannot load accounts: " + message(failure));
            return 1;
        }

        ConsoleTransactionAdapter console =
                new ConsoleTransactionAdapter(accounting.transactions());
        int status = console.run(parsed.consoleArguments(), out, error);
        if (status != 0) {
            return status;
        }
        try {
            accounting.processUntilIdle();
            return 0;
        } catch (RuntimeException failure) {
            error.println("Workflow failed: " + message(failure));
            return 1;
        }
    }

    private static String message(Exception error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private static void printUsage(PrintStream output) {
        output.println("Usage: accounting --accounts <file> <command>");
        output.println();
        ConsoleTransactionAdapter.printUsage(output);
    }

    private record Arguments(Path accounts, String[] consoleArguments, boolean help) {
        private static Arguments parse(String[] arguments) {
            Path accountFile = null;
            List<String> console = new ArrayList<>();
            for (int index = 0; index < arguments.length; index++) {
                String argument = arguments[index];
                if ("--accounts".equals(argument)) {
                    if (accountFile != null) {
                        throw new IllegalArgumentException("--accounts appears more than once");
                    }
                    if (++index >= arguments.length) {
                        throw new IllegalArgumentException("missing value for --accounts");
                    }
                    accountFile = Path.of(arguments[index]);
                } else {
                    console.add(argument);
                }
            }
            boolean help = console.size() == 1
                    && ("--help".equals(console.getFirst())
                    || "-h".equals(console.getFirst()));
            return new Arguments(accountFile, console.toArray(String[]::new), help);
        }
    }
}
