package dev.minimalaccounting.adapters.persistence.inmemory;

import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.transaction.AccountEffect;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionLine;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ConcurrentTransactionRepository implements Repository<TransactionIdentifier, Transaction> {
    private final InMemoryRepository<Transaction> values = new InMemoryRepository<>(
            Transaction::getId,
            Transaction::setId,
            Transaction::getRevision,
            Transaction::setRevision,
            ConcurrentTransactionRepository::copy);
    private final ConcurrentHashMap<String, Long> requests = new ConcurrentHashMap<>();

    @Override
    public synchronized Optional<Transaction> get(TransactionIdentifier key) {
        Long id = key.id() != null ? key.id() : requests.get(key.requestId());
        return id == null ? Optional.empty() : values.get(id);
    }

    @Override
    public synchronized long add(Transaction value) {
        if (requests.containsKey(value.getRequestId())) {
            throw new IllegalStateException("Transaction already exists: " + value.getRequestId());
        }
        long id = values.add(value);
        requests.put(value.getRequestId(), id);
        return id;
    }

    @Override
    public synchronized void update(Transaction value) {
        values.update(value);
    }

    @Override
    public synchronized void remove(TransactionIdentifier key) {
        Long id = key.id() != null ? key.id() : requests.get(key.requestId());
        if (id != null) {
            values.remove(id).ifPresent(value -> requests.remove(value.getRequestId()));
        }
    }

    private static Transaction copy(Transaction value) {
        ArrayList<TransactionLine> lines = new ArrayList<>();
        for (TransactionLine line : value.getLines()) {
            lines.add(new TransactionLine(
                    line.getAccountId(), line.getAccountNumber(), line.getSide(),
                    line.getTransactionAmount(), line.getLedgerAmount()));
        }
        ArrayList<AccountEffect> effects = new ArrayList<>();
        for (AccountEffect effect : value.getEffects()) {
            effects.add(new AccountEffect(
                    effect.getAccountId(), effect.getAccountNumber(), effect.getSide(),
                    effect.getLedgerAmount(), effect.getState()));
        }
        return new Transaction(
                value.getId(), value.getRevision(), value.getState(), value.getRequestId(),
                value.getDocumentType(), value.getDocumentNumber(), value.getDescription(),
                value.getOccurredAt(), value.getReceivedAt(), value.getHeldAt(),
                value.getFailureReason(), lines, effects);
    }
}
