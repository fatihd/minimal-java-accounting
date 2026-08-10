package dev.minimalaccounting.adapters.persistence.postgresql;

import dev.minimalaccounting.model.transaction.AccountEffect;
import dev.minimalaccounting.model.transaction.AccountEffectState;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionLine;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.TransactionState;
import dev.minimalaccounting.shared.ports.ExplicitUpdateRepositoryContract;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresqlTransactionRepositoryTest
        extends ExplicitUpdateRepositoryContract<TransactionIdentifier, Transaction> {
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
                                new AccountNumber("DEBIT001"), DebitCredit.DEBIT,
                                AMOUNT, AMOUNT),
                        new TransactionLine(
                                new AccountNumber("CREDIT01"), DebitCredit.CREDIT,
                                AMOUNT, AMOUNT)),
                Map.of());
        transaction.start(Instant.EPOCH);
        return transaction;
    }

    @Override
    protected TransactionIdentifier add(Transaction value) {
        return repository.add(value);
    }

    @Override
    protected Optional<Transaction> get(TransactionIdentifier identifier) {
        return repository.get(identifier);
    }

    @Override
    protected void update(Transaction value) {
        repository.update(value);
    }

    @Override
    protected void firstChange(Transaction value) {
        value.placeEffect(
                new AccountNumber("DEBIT001"), Instant.EPOCH.plusSeconds(1));
    }

    @Override
    protected void secondChange(Transaction value) {
        value.placeEffect(
                new AccountNumber("CREDIT01"), Instant.EPOCH.plusSeconds(2));
    }

    @Override
    protected Object stateOf(Transaction value) {
        return new State(
                value.getState(),
                value.getEffects().values().stream().map(AccountEffect::getState).toList());
    }

    @Test
    void preservesLinesAndResolvesAndRemovesByRequest() {
        Transaction transaction = createValue();
        repository.add(transaction);

        Transaction loaded = repository.get(TransactionIdentifier.request("request"))
                .orElseThrow();
        assertEquals(new AccountNumber("DEBIT001"),
                loaded.getLines().getFirst().getAccountNumber());

        repository.remove(TransactionIdentifier.request("request"));

        assertFalse(repository.get(TransactionIdentifier.request("request")).isPresent());
    }

    @Test
    void rejectsDuplicateRequestsAndStaleUpdates() {
        Transaction transaction = createValue();
        repository.add(transaction);
        Transaction stale = repository.get(TransactionIdentifier.id(transaction.getId()))
                .orElseThrow();
        transaction.placeEffect(
                new AccountNumber("DEBIT001"), Instant.EPOCH.plusSeconds(1));
        repository.update(transaction);

        assertThrows(IllegalStateException.class, () -> repository.add(createValue()));
        assertThrows(IllegalStateException.class, () -> repository.update(stale));
    }

    private record State(TransactionState transaction, List<AccountEffectState> effects) {
    }
}
