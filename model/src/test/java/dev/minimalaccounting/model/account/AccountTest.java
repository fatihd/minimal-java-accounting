package dev.minimalaccounting.model.account;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;
import org.junit.jupiter.api.Test;

import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);

    @Test
    void placesDebitHold() {
        Account account = account();

        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertEquals(100, account.getDebitsHeld().minor());
    }

    @Test
    void releasesCreditHold() {
        Account account = account();
        account.placeHold(10, 0, DebitCredit.CREDIT, AMOUNT);

        account.releaseHold(10, 0, DebitCredit.CREDIT, AMOUNT);

        assertEquals(0, account.getCreditsHeld().minor());
    }

    @Test
    void settlesDebitHoldIntoBalance() {
        Account account = account();
        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        account.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertEquals(1100, account.getBalance().minor());
        assertEquals(0, account.getDebitsHeld().minor());
    }

    @Test
    void duplicatePlacementDoesNotIncreaseTheHold() {
        Account account = account();
        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        AccountHoldState state = account.placeHold(
                10, 0, DebitCredit.DEBIT, AMOUNT);

        assertEquals(AccountHoldState.HELD, state);
        assertEquals(100, account.getDebitsHeld().minor());
    }

    @Test
    void duplicateReleaseConfirmsTheReleasedState() {
        Account account = account();
        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        account.releaseHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        account.releaseHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertEquals(AccountHoldState.RELEASED, account.getHolds().getFirst().getState());
        assertEquals(0, account.getDebitsHeld().minor());
    }

    @Test
    void duplicateSettlementDoesNotPostTheBalanceTwice() {
        Account account = account();
        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        account.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        account.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertEquals(AccountHoldState.SETTLED, account.getHolds().getFirst().getState());
        assertEquals(1100, account.getBalance().minor());
    }

    @Test
    void latePlacementDoesNotReopenATerminalHold() {
        Account released = account();
        released.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        released.releaseHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        Account settled = account();
        settled.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        settled.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertEquals(
                AccountHoldState.RELEASED,
                released.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT));
        assertEquals(
                AccountHoldState.SETTLED,
                settled.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT));
        assertEquals(0, released.getDebitsHeld().minor());
        assertEquals(1100, settled.getBalance().minor());
    }

    @Test
    void rejectsConflictingDataForTheSameHoldIdentity() {
        Account account = account();
        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertThrows(
                IllegalArgumentException.class,
                () -> account.placeHold(
                        10, 0, DebitCredit.CREDIT, AMOUNT));
    }

    @Test
    void rejectsReleaseOrSettlementBeforePlacement() {
        Account account = account();

        assertThrows(
                IllegalStateException.class,
                () -> account.releaseHold(10, 0, DebitCredit.DEBIT, AMOUNT));
        assertThrows(
                IllegalStateException.class,
                () -> account.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT));
    }

    @Test
    void rejectsConflictingTerminalOperations() {
        Account released = account();
        released.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        released.releaseHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        Account settled = account();
        settled.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        settled.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT);

        assertThrows(
                IllegalStateException.class,
                () -> released.settleHold(10, 0, DebitCredit.DEBIT, AMOUNT));
        assertThrows(
                IllegalStateException.class,
                () -> settled.releaseHold(10, 0, DebitCredit.DEBIT, AMOUNT));
    }

    @Test
    void rejectedHoldRetainsItsIdempotencyState() {
        Account account = new Account(
                1L,
                0L,
                new AccountNumber("ACCOUNT01"),
                "account",
                new Money(1000, USD),
                null,
                new Money(1050, USD),
                List.of());

        assertThrows(
                PostingRejectedException.class,
                () -> account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT));
        assertThrows(
                PostingRejectedException.class,
                () -> account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT));
        assertEquals(0, account.getDebitsHeld().minor());
        assertEquals(1, account.getHolds().size());
        assertEquals(AccountHoldState.DECLINED, account.getHolds().getFirst().getState());
    }

    private static Account account() {
        return new Account(
                1L,
                0L,
                new AccountNumber("ACCOUNT01"),
                "account",
                new Money(1000, USD),
                null,
                null,
                List.of());
    }
}
