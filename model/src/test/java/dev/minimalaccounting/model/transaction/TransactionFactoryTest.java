package dev.minimalaccounting.model.transaction;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import dev.minimalaccounting.model.account.Account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionFactoryTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Money AMOUNT = new Money(100, USD);

    @Nested
    class CreatedTransaction {
        private Account account;
        private Transaction transaction;

        @BeforeEach
        void createTransaction() {
            account = account("ACCOUNT07");
            transaction = TransactionFactory.create(
                    "request",
                    "TRANSFER",
                    "document",
                    "description",
                    Instant.EPOCH,
                    List.of(new TransactionFactory.Line(
                            account, DebitCredit.DEBIT, AMOUNT, AMOUNT)));
        }

        @Test
        void copiesCanonicalAccountNumber() {
            assertEquals(
                    new AccountNumber("ACCOUNT07"),
                    transaction.getLines().getFirst().getAccountNumber());
        }

        @Test
        void leavesTransactionUnstarted() {
            assertNull(transaction.getState());
        }

        @Test
        void doesNotMutateAccount() {
            assertEquals(1000, account.getBalance().minor());
        }

    }

    @Test
    void rejectsLedgerCurrencyDifferentFromAccount() {
        Account account = account("ACCOUNT07");

        assertThrows(
                PostingRejectedException.class,
                () -> TransactionFactory.create(
                        "request",
                        "TRANSFER",
                        "document",
                        "description",
                        Instant.EPOCH,
                        List.of(new TransactionFactory.Line(
                                account,
                                DebitCredit.DEBIT,
                                AMOUNT,
                                new Money(100, EUR)))));
    }

    private static Account account(String number) {
        Account account = Account.create(
                new AccountNumber(number),
                number,
                new Money(1000, USD),
                null,
                null);
        account.setRevision(0L);
        return account;
    }
}
