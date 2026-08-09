package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.ports.persistence.ExplicitUpdateRepositoryContract;
import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentAccountRepositoryTest extends ExplicitUpdateRepositoryContract<Account> {
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
    protected long add(Account value) {
        return repository.add(value);
    }

    @Override
    protected Optional<Account> get(long id) {
        return repository.get(AccountReference.id(id));
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
    void resolvesAnAccountByItsNumber() {
        Account account = createValue();
        repository.add(account);

        assertEquals(
                account.getId(),
                repository.get(AccountReference.number(NUMBER)).orElseThrow().getId());
    }

    @Test
    void rejectsASecondAccountWithTheSameNumber() {
        repository.add(createValue());

        assertThrows(IllegalStateException.class, () -> repository.add(createValue()));
    }

    @Test
    void removingByNumberAlsoClearsItsLookup() {
        repository.add(createValue());

        repository.remove(AccountReference.number(NUMBER));

        assertFalse(repository.get(AccountReference.number(NUMBER)).isPresent());
    }
}
