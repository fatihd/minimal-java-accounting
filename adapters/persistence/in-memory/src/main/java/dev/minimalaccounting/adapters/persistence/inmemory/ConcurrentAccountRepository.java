package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountHold;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.ports.persistence.AccountRepository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConcurrentAccountRepository implements AccountRepository {
    private final InMemoryRepository<Account> values = new InMemoryRepository<>(
            Account::getId,
            Account::setId,
            Account::getRevision,
            Account::setRevision,
            ConcurrentAccountRepository::copy);
    private final ConcurrentHashMap<String, Long> numbers = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<Account> get(AccountReference key) {
        Long id = key.id() != null ? key.id() : numbers.get(key.accountNumber().value());
        return id == null ? Optional.empty() : values.get(id);
    }

    @Override
    public synchronized long add(Account value) {
        if (numbers.containsKey(value.getAccountNumber().value())) {
            throw new IllegalStateException("Account already exists");
        }
        long id = values.add(value);
        numbers.put(value.getAccountNumber().value(), id);
        return id;
    }

    @Override
    public synchronized void update(Account value) {
        values.update(value);
    }

    @Override
    public synchronized void remove(AccountReference key) {
        Long id = key.id() != null ? key.id() : numbers.get(key.accountNumber().value());
        if (id != null) {
            values.remove(id).ifPresent(
                    value -> numbers.remove(value.getAccountNumber().value()));
        }
    }

    private static Account copy(Account value) {
        var holds = value.getHolds().stream()
                .map(hold -> new AccountHold(
                        hold.getTransactionId(), hold.getEffectNumber(), hold.getSide(),
                        hold.getAmount(), hold.getState(), hold.getFailureReason()))
                .toList();
        return new Account(
                value.getId(), value.getRevision(), value.getAccountNumber(), value.getName(),
                value.getBalance(), value.getMinimumBalance(), value.getMaximumBalance(), holds);
    }
}
