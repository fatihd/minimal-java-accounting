package dev.minimalaccounting.app;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.ports.api.Transaction;
import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.core.transaction.TransactionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountingFlowTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private AccountingFixture accounting;

    @Nested
    class CompletedTransaction {
        @BeforeEach
        void setUp() {
            accounting = new AccountingFixture(CLOCK);
            accounting.addAccount("DEBIT001", 1000, USD);
            accounting.addAccount("CREDIT01", 1000, USD);
            accounting.processor.submit(transaction());
            accounting.processUntilIdle();
        }

        @Test
        void createsJournalEntry() {
            assertTrue(accounting.journals.get(
                    JournalReference.request("request-1")).isPresent());
        }

        @Test
        void settlesDebitAccount() {
            assertEquals(1100, balance("DEBIT001"));
        }

        @Test
        void settlesCreditAccount() {
            assertEquals(900, balance("CREDIT01"));
        }

        @Test
        void leavesMessagingIdle() {
            assertEquals(0, accounting.pendingMessages());
        }

        @Test
        void completedTransactionCanBeSubmittedAgainWithoutSettlingAgain() {
            accounting.processor.submit(transaction());
            accounting.processUntilIdle();

            assertEquals(1100, balance("DEBIT001"));
        }
    }

    @Nested
    class DeclinedAccountHold {
        @BeforeEach
        void setUp() {
            accounting = new AccountingFixture(CLOCK);
            accounting.addAccount("DEBIT001", 1000, USD, null, 1050L);
            accounting.addAccount("CREDIT01", 1000, USD);
            accounting.processor.submit(transaction());
            accounting.processUntilIdle();
        }

        @Test
        void doesNotCreateJournalEntry() {
            assertTrue(accounting.journals.get(
                    JournalReference.request("request-1")).isEmpty());
        }

        @Test
        void leavesDebitBalanceUnchanged() {
            assertEquals(1000, balance("DEBIT001"));
        }

        @Test
        void leavesCreditBalanceUnchanged() {
            assertEquals(1000, balance("CREDIT01"));
        }

        @Test
        void releasesOtherAccountHold() {
            assertEquals(0, creditsHeld("CREDIT01"));
        }

        @Test
        void rejectsTransaction() {
            assertEquals(TransactionState.REJECTED,
                    accounting.transactions.get(
                            TransactionReference.request("request-1"))
                            .orElseThrow()
                            .getState());
        }

        @Test
        void leavesMessagingIdle() {
            assertEquals(0, accounting.pendingMessages());
        }
    }

    @Nested
    class TransactionWithoutAccountEffects {
        @BeforeEach
        void setUp() {
            accounting = new AccountingFixture(CLOCK);
            accounting.addAccount("ACCOUNT1", 1000, USD);
            accounting.processor.submit(transactionWithoutAccountEffects());
            accounting.processUntilIdle();
        }

        @Test
        void createsJournalEntry() {
            assertTrue(accounting.journals.get(
                    JournalReference.request("request-without-effects")).isPresent());
        }

        @Test
        void removesCompletedTransaction() {
            assertTrue(accounting.transactions.get(
                    TransactionReference.request("request-without-effects")).isEmpty());
        }

        @Test
        void leavesAccountBalanceUnchanged() {
            assertEquals(1000, balance("ACCOUNT1"));
        }
    }

    private long balance(String number) {
        return account(number).getBalance().minor();
    }

    private long creditsHeld(String number) {
        return account(number).getCreditsHeld().minor();
    }

    private Account account(String number) {
        return accounting.accounts.get(AccountReference.number(new AccountNumber(number)))
                .orElseThrow();
    }

    private static Transaction transaction() {
        Money amount = new Money(100, USD);
        return new Transaction(
                "request-1",
                "TRANSFER",
                "DOC-1",
                "demo",
                Instant.EPOCH,
                List.of(
                        new Transaction.Line(
                                new AccountNumber("DEBIT001"),
                                DebitCredit.DEBIT,
                                amount,
                                amount),
                        new Transaction.Line(
                                new AccountNumber("CREDIT01"),
                                DebitCredit.CREDIT,
                                amount,
                                amount)));
    }

    private static Transaction transactionWithoutAccountEffects() {
        Money amount = new Money(100, USD);
        return new Transaction(
                "request-without-effects",
                "TRANSFER",
                "DOC-2",
                "net zero",
                Instant.EPOCH,
                List.of(
                        new Transaction.Line(
                                new AccountNumber("ACCOUNT1"),
                                DebitCredit.DEBIT,
                                amount,
                                amount),
                        new Transaction.Line(
                                new AccountNumber("ACCOUNT1"),
                                DebitCredit.CREDIT,
                                amount,
                                amount)));
    }
}
