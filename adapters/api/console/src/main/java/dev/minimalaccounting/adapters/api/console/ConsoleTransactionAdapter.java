package dev.minimalaccounting.adapters.api.console;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.ports.api.Transaction;
import dev.minimalaccounting.ports.api.TransactionProcessor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public final class ConsoleTransactionAdapter {
    private static final String USAGE = """
            Command:
              submit \\
                --request-id <id> \\
                --document-type <type> \\
                --document-number <number> \\
                [--description <text>] \\
                --occurred-at <ISO-8601 instant> \\
                --line <account> <DEBIT|CREDIT> \\
                       <transaction-minor> <transaction-currency> \\
                       <ledger-minor> <ledger-currency> \\
                --line ...
            """;

    @NonNull
    private final TransactionProcessor transactions;

    public int run(
            @NonNull String[] arguments,
            @NonNull PrintStream out,
            @NonNull PrintStream error) {
        if (isHelp(arguments)) {
            printUsage(out);
            return 0;
        }

        Transaction transaction;
        try {
            transaction = parse(arguments);
        } catch (RuntimeException invalid) {
            error.println("Invalid command: " + message(invalid));
            printUsage(error);
            return 2;
        }

        try {
            transactions.submit(transaction);
            out.println("accepted " + transaction.requestId());
            return 0;
        } catch (RuntimeException rejected) {
            error.println("Transaction rejected: " + message(rejected));
            return 1;
        }
    }

    public static void printUsage(@NonNull PrintStream output) {
        output.print(USAGE);
    }

    private static Transaction parse(String[] arguments) {
        if (arguments.length == 0 || !"submit".equals(arguments[0])) {
            throw new IllegalArgumentException("expected submit");
        }
        String requestId = null;
        String documentType = null;
        String documentNumber = null;
        String description = "";
        boolean descriptionSet = false;
        Instant occurredAt = null;
        List<Transaction.Line> lines = new ArrayList<>();

        for (int index = 1; index < arguments.length; index++) {
            String option = arguments[index];
            switch (option) {
                case "--request-id" -> {
                    requireAbsent(requestId, option);
                    requestId = value(arguments, ++index, option);
                }
                case "--document-type" -> {
                    requireAbsent(documentType, option);
                    documentType = value(arguments, ++index, option);
                }
                case "--document-number" -> {
                    requireAbsent(documentNumber, option);
                    documentNumber = value(arguments, ++index, option);
                }
                case "--description" -> {
                    if (descriptionSet) {
                        throw new IllegalArgumentException(option + " appears more than once");
                    }
                    descriptionSet = true;
                    description = value(arguments, ++index, option);
                }
                case "--occurred-at" -> {
                    requireAbsent(occurredAt, option);
                    occurredAt = Instant.parse(value(arguments, ++index, option));
                }
                case "--line" -> {
                    if (index + 6 >= arguments.length) {
                        throw new IllegalArgumentException(option + " needs six values");
                    }
                    lines.add(line(arguments, index + 1));
                    index += 6;
                }
                default -> throw new IllegalArgumentException("unknown option " + option);
            }
        }

        return new Transaction(
                requiredText(requestId, "--request-id"),
                requiredText(documentType, "--document-type"),
                requiredText(documentNumber, "--document-number"),
                description,
                required(occurredAt, "--occurred-at"),
                lines);
    }

    private static Transaction.Line line(String[] arguments, int offset) {
        try {
            return new Transaction.Line(
                    new AccountNumber(arguments[offset]),
                    DebitCredit.valueOf(arguments[offset + 1].toUpperCase(Locale.ROOT)),
                    new Money(
                            Long.parseLong(arguments[offset + 2]),
                            Currency.getInstance(arguments[offset + 3].toUpperCase(Locale.ROOT))),
                    new Money(
                            Long.parseLong(arguments[offset + 4]),
                            Currency.getInstance(arguments[offset + 5].toUpperCase(Locale.ROOT))));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("invalid --line: " + message(invalid), invalid);
        }
    }

    private static String value(String[] arguments, int index, String option) {
        if (index >= arguments.length || arguments[index].startsWith("--")) {
            throw new IllegalArgumentException("missing value for " + option);
        }
        return arguments[index];
    }

    private static String requiredText(String value, String option) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing " + option);
        }
        return value;
    }

    private static <T> T required(T value, String option) {
        if (value == null) {
            throw new IllegalArgumentException("missing " + option);
        }
        return value;
    }

    private static void requireAbsent(Object current, String option) {
        if (current != null) {
            throw new IllegalArgumentException(option + " appears more than once");
        }
    }

    private static boolean isHelp(String[] arguments) {
        return arguments.length == 1
                && ("--help".equals(arguments[0]) || "-h".equals(arguments[0]));
    }

    private static String message(RuntimeException error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }
}
