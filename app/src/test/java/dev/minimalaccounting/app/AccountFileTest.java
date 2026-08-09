package dev.minimalaccounting.app;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.core.account.AccountReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AccountFileTest {
    @TempDir
    Path directory;

    @Test
    void loadsAccountsIntoRepositoryPort() throws Exception {
        Path file = directory.resolve("accounts.csv");
        Files.writeString(file, """
                account_number,name,opening_balance_minor,currency,minimum_balance_minor,maximum_balance_minor
                ACCOUNT01,Operating account,1250,USD,0,5000
                """);
        ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();

        AccountFile.load(file, accounts);

        var account = accounts.get(AccountReference.number(new AccountNumber("ACCOUNT01")))
                .orElseThrow();
        assertEquals("Operating account", account.getName());
        assertEquals(1250, account.getBalance().minor());
        assertEquals(0, account.getMinimumBalance().minor());
        assertEquals(5000, account.getMaximumBalance().minor());
    }
}
