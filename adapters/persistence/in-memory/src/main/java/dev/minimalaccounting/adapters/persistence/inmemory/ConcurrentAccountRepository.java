package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountHold;
import dev.minimalaccounting.model.account.AccountIdentifier;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ConcurrentAccountRepository implements Repository<AccountIdentifier, Account> {
    private final AtomicLong ids = new AtomicLong();
    private final InMemoryRepository<Long, Account> values = new InMemoryRepository<>(
            ignored -> ids.incrementAndGet(),
            Account::getId,
            Account::setId,
            Account::getRevision,
            Account::setRevision,
            ConcurrentAccountRepository::copy);
    private final ConcurrentHashMap<String, Long> numbers = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<Account> get(AccountIdentifier key) {
        Long id = key.id() != null ? key.id() : numbers.get(key.accountNumber().value());
        return id == null ? Optional.empty() : values.get(id);
    }

    @Override
    public synchronized AccountIdentifier add(Account value) {
        if (numbers.containsKey(value.getAccountNumber().value())) {
            throw new IllegalStateException("Account already exists");
        }
        long id = values.add(value);
        numbers.put(value.getAccountNumber().value(), id);
        return AccountIdentifier.id(id);
    }

    @Override
    public synchronized void update(Account value) {
        values.update(value);
    }

    @Override
    public synchronized void remove(AccountIdentifier key) {
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
