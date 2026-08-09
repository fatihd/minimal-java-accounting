package dev.minimalaccounting.ports.persistence;

import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;

import java.util.Optional;

public interface AccountRepository {
    Optional<Account> get(AccountReference key);

    long add(Account value);

    void update(Account value);

    void remove(AccountReference key);
}
