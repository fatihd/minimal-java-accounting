package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.model.account.Account;
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

class ConcurrentAccountRepositoryTest
        extends ExplicitUpdateRepositoryContract<AccountNumber, Account> {
    private static final Currency USD = Currency.getInstance("USD");
    private static final AccountNumber NUMBER = new AccountNumber("ACCOUNT01");
    private static final Money HOLD = new Money(10, USD);

    private ConcurrentAccountRepository repository;

    @BeforeEach
    void createRepository() {
        repository = new ConcurrentAccountRepository();
    }

    @Override
    protected Account createValue() {
        return Account.create(NUMBER, "account", new Money(100, USD), null, null);
    }

    @Override
    protected AccountNumber add(Account value) {
        return repository.add(value);
    }

    @Override
    protected Optional<Account> get(AccountNumber identifier) {
        return repository.get(identifier);
    }

    @Override
    protected void update(Account value) {
        repository.update(value);
    }

    @Override
    protected void firstChange(Account value) {
        value.placeHold(10, DebitCredit.DEBIT, HOLD);
    }

    @Override
    protected void secondChange(Account value) {
        value.placeHold(11, DebitCredit.DEBIT, HOLD);
    }

    @Override
    protected Object stateOf(Account value) {
        return value.getDebitsHeld();
    }

    @Test
    void usesTheAccountNumberAsItsIdentity() {
        Account account = createValue();
        AccountNumber identifier = repository.add(account);

        assertEquals(NUMBER, identifier);
        assertEquals(NUMBER, repository.get(NUMBER).orElseThrow().getAccountNumber());
    }

    @Test
    void rejectsASecondAccountWithTheSameNumber() {
        repository.add(createValue());

        assertThrows(IllegalStateException.class, () -> repository.add(createValue()));
    }

    @Test
    void removingByNumberAlsoClearsItsLookup() {
        repository.add(createValue());

        repository.remove(NUMBER);

        assertFalse(repository.get(NUMBER).isPresent());
    }
}
