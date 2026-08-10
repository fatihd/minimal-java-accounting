package dev.minimalaccounting.app;

import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.account.Account;

import lombok.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Currency;
import java.util.List;

final class AccountFile {
    private static final String HEADER = String.join(",",
            "account_number",
            "name",
            "opening_balance_minor",
            "currency",
            "minimum_balance_minor",
            "maximum_balance_minor");

    private AccountFile() {
    }

    static void load(
            @NonNull Path path,
            @NonNull Repository<AccountNumber, Account> accounts) throws IOException {
        List<String> lines = Files.readAllLines(path);
        boolean headerSeen = false;
        int accountCount = 0;
        for (int index = 0; index < lines.size(); index++) {
            String value = lines.get(index).trim();
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                if (!HEADER.equals(value)) {
                    throw invalid(index, "expected header " + HEADER);
                }
                headerSeen = true;
                continue;
            }
            try {
                Account account = account(value);
                if (accounts.get(account.getAccountNumber()).isEmpty()) {
                    accounts.add(account);
                }
                accountCount++;
            } catch (RuntimeException invalid) {
                throw invalid(index, message(invalid));
            }
        }
        if (!headerSeen) {
            throw new IllegalArgumentException("Account file has no header");
        }
        if (accountCount == 0) {
            throw new IllegalArgumentException("Account file has no accounts");
        }
    }

    private static Account account(String row) {
        String[] fields = row.split(",", -1);
        if (fields.length != 6) {
            throw new IllegalArgumentException("expected six comma-separated fields");
        }
        for (int index = 0; index < fields.length; index++) {
            fields[index] = fields[index].trim();
        }
        AccountNumber number = new AccountNumber(required(fields[0], "account_number"));
        String name = required(fields[1], "name");
        long openingBalance = Long.parseLong(required(fields[2], "opening_balance_minor"));
        Currency currency = Currency.getInstance(required(fields[3], "currency"));
        return Account.create(
                number,
                name,
                new Money(openingBalance, currency),
                optionalMoney(fields[4], currency),
                optionalMoney(fields[5], currency));
    }

    private static Money optionalMoney(String minor, Currency currency) {
        return minor.isBlank() ? null : new Money(Long.parseLong(minor), currency);
    }

    private static String required(String value, String name) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static IllegalArgumentException invalid(int zeroBasedLine, String problem) {
        return new IllegalArgumentException(
                "Invalid account file line " + (zeroBasedLine + 1) + ": " + problem);
    }

    private static String message(RuntimeException error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }
}
