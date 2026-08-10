package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.shared.ports.ExplicitUpdateRepositoryContract;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresqlAccountRepositoryTest
        extends ExplicitUpdateRepositoryContract<AccountIdentifier, Account> {
    private static final Currency USD = Currency.getInstance("USD");
    private static final AccountNumber NUMBER = new AccountNumber("ACCOUNT01");
    private static final Money HOLD = new Money(10, USD);

    private final PostgresqlTest database = new PostgresqlTest();
    private PostgresqlAccountRepository repository;

    @BeforeEach
    void createRepository() {
        database.resetDatabase();
        repository = new PostgresqlAccountRepository(database.transactions);
    }

    @Override
    protected Account createValue() {
        return Account.create(NUMBER, "account", new Money(100, USD), null, null);
    }

    @Override
    protected AccountIdentifier add(Account value) {
        return repository.add(value);
    }

    @Override
    protected Optional<Account> get(AccountIdentifier identifier) {
        return repository.get(identifier);
    }

    @Override
    protected void update(Account value) {
        repository.update(value);
    }

    @Override
    protected void firstChange(Account value) {
        value.placeHold(10, 0, DebitCredit.DEBIT, HOLD);
    }

    @Override
    protected void secondChange(Account value) {
        value.placeHold(11, 0, DebitCredit.DEBIT, HOLD);
    }

    @Override
    protected Object stateOf(Account value) {
        return value.getDebitsHeld();
    }

    @Test
    void resolvesByNumberAndRemovesByNumber() {
        Account account = createValue();
        repository.add(account);

        assertEquals(account.getId(), repository.get(AccountIdentifier.number(NUMBER))
                .orElseThrow().getId());

        repository.remove(AccountIdentifier.number(NUMBER));

        assertFalse(repository.get(AccountIdentifier.number(NUMBER)).isPresent());
    }

    @Test
    void rejectsDuplicateNumbersAndStaleUpdates() {
        Account account = createValue();
        repository.add(account);
        Account stale = repository.get(AccountIdentifier.id(account.getId())).orElseThrow();
        account.placeHold(10, 0, DebitCredit.DEBIT, HOLD);
        repository.update(account);

        assertThrows(IllegalStateException.class, () -> repository.add(createValue()));
        assertThrows(IllegalStateException.class, () -> repository.update(stale));
    }
}
