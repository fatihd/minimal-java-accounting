package dev.minimalaccounting.app;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.model.transaction.TransactionState;
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
            accounting.submitTransaction.handle(transaction());
            accounting.processUntilIdle();
        }

        @Test
        void createsJournalEntry() {
            assertTrue(accounting.journals.get(
                    JournalIdentifier.request("request-1")).isPresent());
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
            accounting.submitTransaction.handle(transaction());
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
            accounting.submitTransaction.handle(transaction());
            accounting.processUntilIdle();
        }

        @Test
        void doesNotCreateJournalEntry() {
            assertTrue(accounting.journals.get(
                    JournalIdentifier.request("request-1")).isEmpty());
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
                            TransactionIdentifier.request("request-1"))
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
            accounting.submitTransaction.handle(transactionWithoutAccountEffects());
            accounting.processUntilIdle();
        }

        @Test
        void createsJournalEntry() {
            assertTrue(accounting.journals.get(
                    JournalIdentifier.request("request-without-effects")).isPresent());
        }

        @Test
        void removesCompletedTransaction() {
            assertTrue(accounting.transactions.get(
                    TransactionIdentifier.request("request-without-effects")).isEmpty());
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
        return accounting.accounts.get(new AccountNumber(number))
                .orElseThrow();
    }

    private static SubmitTransaction transaction() {
        Money amount = new Money(100, USD);
        return new SubmitTransaction(
                "request-1",
                "TRANSFER",
                "DOC-1",
                "demo",
                Instant.EPOCH,
                List.of(
                        new SubmitTransaction.Line(
                                new AccountNumber("DEBIT001"),
                                DebitCredit.DEBIT,
                                amount,
                                amount),
                        new SubmitTransaction.Line(
                                new AccountNumber("CREDIT01"),
                                DebitCredit.CREDIT,
                                amount,
                                amount)));
    }

    private static SubmitTransaction transactionWithoutAccountEffects() {
        Money amount = new Money(100, USD);
        return new SubmitTransaction(
                "request-without-effects",
                "TRANSFER",
                "DOC-2",
                "net zero",
                Instant.EPOCH,
                List.of(
                        new SubmitTransaction.Line(
                                new AccountNumber("ACCOUNT1"),
                                DebitCredit.DEBIT,
                                amount,
                                amount),
                        new SubmitTransaction.Line(
                                new AccountNumber("ACCOUNT1"),
                                DebitCredit.CREDIT,
                                amount,
                                amount)));
    }
}
