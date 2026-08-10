package dev.minimalaccounting.adapters.persistence.inmemory;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrentTransactionRepositoryTest
        extends ExplicitUpdateRepositoryContract<TransactionIdentifier, Transaction> {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);

    private ConcurrentTransactionRepository repository;

    @BeforeEach
    void createRepository() {
        repository = new ConcurrentTransactionRepository();
    }

    @Override
    protected Transaction createValue() {
        return new Transaction(
                null,
                null,
                null,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                null,
                null,
                null,
                List.of(
                        new TransactionLine(
                                new AccountNumber("DEBIT001"), DebitCredit.DEBIT,
                                AMOUNT, AMOUNT),
                        new TransactionLine(
                                new AccountNumber("CREDIT01"), DebitCredit.CREDIT,
                                AMOUNT, AMOUNT)),
                List.of());
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
        value.start(Instant.EPOCH);
    }

    @Override
    protected void secondChange(Transaction value) {
        value.placeEffect(new AccountNumber("DEBIT001"), 0, Instant.EPOCH.plusSeconds(1));
    }

    @Override
    protected Object stateOf(Transaction value) {
        return new State(
                value.getState(),
                value.getEffects().stream().map(AccountEffect::getState).toList());
    }

    @Test
    void preservesResolvedAccountNumber() {
        Transaction transaction = createValue();
        TransactionIdentifier identifier = repository.add(transaction);

        assertEquals(
                new AccountNumber("DEBIT001"),
                repository.get(identifier)
                        .orElseThrow()
                        .getLines()
                        .getFirst()
                        .getAccountNumber());
    }

    @Test
    void resolvesATransactionByItsRequest() {
        Transaction transaction = createValue();
        repository.add(transaction);

        assertEquals(
                transaction.getId(),
                repository.get(TransactionIdentifier.request("request"))
                        .orElseThrow()
                        .getId());
    }

    @Test
    void rejectsASecondTransactionWithTheSameRequest() {
        repository.add(createValue());

        assertThrows(IllegalStateException.class, () -> repository.add(createValue()));
    }

    @Test
    void removingByRequestAlsoClearsItsLookup() {
        repository.add(createValue());

        repository.remove(TransactionIdentifier.request("request"));

        assertFalse(repository.get(TransactionIdentifier.request("request")).isPresent());
    }

    private record State(TransactionState transaction, List<AccountEffectState> effects) {
    }
}
