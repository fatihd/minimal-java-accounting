package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcTransactionsTest {
    private final PostgresqlTest database = new PostgresqlTest();
    private PostgresqlAccountRepository accounts;

    @BeforeEach
    void createRepository() {
        database.resetDatabase();
        accounts = new PostgresqlAccountRepository(database.transactions);
    }

    @Test
    void rollsBackRepositoryWorkWhenTheUnitFails() {
        Account account = account();

        assertThrows(ExpectedFailure.class, () -> database.transactions.inTransaction(() -> {
            accounts.add(account);
            throw new ExpectedFailure();
        }));

        assertFalse(accounts.get(AccountReference.number(account.getAccountNumber()))
                .isPresent());
    }

    @Test
    void nestedWorkUsesTheSameTransactionAndCommitsOnce() {
        Account account = account();

        String result = database.transactions.inTransaction(() -> {
            assertSame(
                    database.transactions.requiredConnection(),
                    database.transactions.requiredConnection());
            accounts.add(account);
            database.transactions.inTransaction(() -> accounts.update(account));
            return "committed";
        });

        assertEquals("committed", result);
        assertEquals(1L, accounts.get(AccountReference.id(account.getId()))
                .orElseThrow().getRevision());
    }

    @Test
    void schemaInstallationIsIdempotent() {
        PostgresqlSchema.install(database.transactions);

        long migrations = database.transactions.withConnection(connection -> {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery(
                         "SELECT COUNT(*) FROM accounting_schema_history")) {
                result.next();
                return result.getLong(1);
            }
        });

        assertEquals(1, migrations);
    }

    private static Account account() {
        Currency usd = Currency.getInstance("USD");
        return Account.create(
                new AccountNumber("ACCOUNT01"), "account", new Money(100, usd), null, null);
    }

    private static final class ExpectedFailure extends RuntimeException {
    }
}
