package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.core.transaction.AccountEffect;
import dev.minimalaccounting.core.transaction.AccountEffectState;
import dev.minimalaccounting.core.transaction.Transaction;
import dev.minimalaccounting.core.transaction.TransactionLine;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.core.transaction.TransactionState;
import dev.minimalaccounting.ports.persistence.ExplicitUpdateRepositoryContract;
import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresqlTransactionRepositoryTest
        extends ExplicitUpdateRepositoryContract<Transaction> {
    private static final Money AMOUNT =
            new Money(100, Currency.getInstance("USD"));

    private final PostgresqlTest database = new PostgresqlTest();
    private PostgresqlTransactionRepository repository;

    @BeforeEach
    void createRepository() {
        database.resetDatabase();
        repository = new PostgresqlTransactionRepository(database.transactions);
    }

    @Override
    protected Transaction createValue() {
        Transaction transaction = new Transaction(
                null, null, null, "request", "TRANSFER", "document", "description",
                Instant.EPOCH, null, null, null,
                List.of(
                        new TransactionLine(
                                41, new AccountNumber("DEBIT001"), DebitCredit.DEBIT,
                                AMOUNT, AMOUNT),
                        new TransactionLine(
                                42, new AccountNumber("CREDIT01"), DebitCredit.CREDIT,
                                AMOUNT, AMOUNT)),
                List.of());
        transaction.start(Instant.EPOCH);
        return transaction;
    }

    @Override
    protected long add(Transaction value) {
        return repository.add(value);
    }

    @Override
    protected Optional<Transaction> get(long id) {
        return repository.get(TransactionReference.id(id));
    }

    @Override
    protected void update(Transaction value) {
        repository.update(value);
    }

    @Override
    protected void firstChange(Transaction value) {
        value.placeEffect(41, 0, Instant.EPOCH.plusSeconds(1));
    }

    @Override
    protected void secondChange(Transaction value) {
        value.placeEffect(42, 1, Instant.EPOCH.plusSeconds(2));
    }

    @Override
    protected Object stateOf(Transaction value) {
        return new State(
                value.getState(),
                value.getEffects().stream().map(AccountEffect::getState).toList());
    }

    @Test
    void preservesLinesAndResolvesAndRemovesByRequest() {
        Transaction transaction = createValue();
        repository.add(transaction);

        Transaction loaded = repository.get(TransactionReference.request("request"))
                .orElseThrow();
        assertEquals(41, loaded.getLines().getFirst().getAccountId());

        repository.remove(TransactionReference.request("request"));

        assertFalse(repository.get(TransactionReference.request("request")).isPresent());
    }

    @Test
    void rejectsDuplicateRequestsAndStaleUpdates() {
        Transaction transaction = createValue();
        repository.add(transaction);
        Transaction stale = repository.get(TransactionReference.id(transaction.getId()))
                .orElseThrow();
        transaction.placeEffect(41, 0, Instant.EPOCH.plusSeconds(1));
        repository.update(transaction);

        assertThrows(IllegalStateException.class, () -> repository.add(createValue()));
        assertThrows(IllegalStateException.class, () -> repository.update(stale));
    }

    private record State(TransactionState transaction, List<AccountEffectState> effects) {
    }
}
