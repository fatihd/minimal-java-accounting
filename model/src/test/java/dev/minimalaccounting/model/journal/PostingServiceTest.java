package dev.minimalaccounting.model.journal;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.model.transaction.TransactionLine;
import dev.minimalaccounting.model.transaction.TransactionState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostingServiceTest {
    private static final Currency USD = Currency.getInstance("USD");

    @Nested
    class BalancedPosting {
        private Account debit;
        private Account credit;
        private JournalEntry entry;

        @BeforeEach
        void post() {
            debit = account(1, "DEBIT001", 1000);
            credit = account(2, "CREDIT01", 1000);
            Transaction transaction = transaction(
                    new TransactionLine(
                            1, new AccountNumber("DEBIT001"), DebitCredit.DEBIT,
                            new Money(100, USD), new Money(100, USD)),
                    new TransactionLine(
                            2, new AccountNumber("CREDIT01"), DebitCredit.CREDIT,
                            new Money(100, USD), new Money(100, USD)));

            entry = new JournalService().journalize(
                    transaction, List.of(debit, credit), Instant.EPOCH);
        }

        @Test
        void recordsEveryTransactionLine() {
            assertEquals(2, entry.getLines().size());
        }

        @Test
        void recordsRunningBalance() {
            assertEquals(1100, entry.getLines().getFirst().getBalanceAfter().minor());
        }

        @Test
        void doesNotMutateDebitAccount() {
            assertEquals(1000, debit.getBalance().minor());
        }

        @Test
        void doesNotMutateCreditAccount() {
            assertEquals(1000, credit.getBalance().minor());
        }
    }

    @Test
    void rejectsAMissingReferencedAccount() {
        Account debit = account(1, "DEBIT001", 1000);
        Transaction transaction = transaction(
                line(1, "DEBIT001", DebitCredit.DEBIT),
                line(2, "CREDIT01", DebitCredit.CREDIT));

        assertThrows(
                PostingRejectedException.class,
                () -> new JournalService().journalize(
                        transaction, List.of(debit), Instant.EPOCH));
    }

    @Test
    void rejectsAnAccountWhoseIdentityChanged() {
        Account changed = account(1, "CHANGED1", 1000);
        Account credit = account(2, "CREDIT01", 1000);
        Transaction transaction = transaction(
                line(1, "DEBIT001", DebitCredit.DEBIT),
                line(2, "CREDIT01", DebitCredit.CREDIT));

        assertThrows(
                PostingRejectedException.class,
                () -> new JournalService().journalize(
                        transaction, List.of(changed, credit), Instant.EPOCH));
    }

    private static Account account(long id, String number, long balance) {
        return new Account(id, 0L, new AccountNumber(number), number,
                new Money(balance, USD), null, null, List.of());
    }

    private static Transaction transaction(TransactionLine... lines) {
        return new Transaction(
                null,
                null,
                TransactionState.HELD,
                "request",
                "TRANSFER",
                "document",
                "description",
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                List.of(lines),
                List.of());
    }

    private static TransactionLine line(
            long accountId,
            String accountNumber,
            DebitCredit side) {
        Money amount = new Money(100, USD);
        return new TransactionLine(
                accountId,
                new AccountNumber(accountNumber),
                side,
                amount,
                amount);
    }
}
