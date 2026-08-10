package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountHold;

import java.util.Optional;

public final class ConcurrentAccountRepository implements Repository<AccountNumber, Account> {
    private final InMemoryRepository<AccountNumber, Account> values = new InMemoryRepository<>(
            Account::getAccountNumber,
            Account::getAccountNumber,
            (account, accountNumber) -> { },
            Account::getRevision,
            Account::setRevision,
            ConcurrentAccountRepository::copy);

    @Override
    public synchronized Optional<Account> get(AccountNumber accountNumber) {
        return values.get(accountNumber);
    }

    @Override
    public synchronized AccountNumber add(Account value) {
        return values.add(value);
    }

    @Override
    public synchronized void update(Account value) {
        values.update(value);
    }

    @Override
    public synchronized void remove(AccountNumber accountNumber) {
        values.remove(accountNumber);
    }

    private static Account copy(Account value) {
        var holds = value.getHolds().stream()
                .map(hold -> new AccountHold(
                        hold.getTransactionId(), hold.getEffectNumber(), hold.getSide(),
                        hold.getAmount(), hold.getState(), hold.getFailureReason()))
                .toList();
        return new Account(
                value.getRevision(), value.getAccountNumber(), value.getName(),
                value.getBalance(), value.getMinimumBalance(), value.getMaximumBalance(), holds);
    }
}
