package dev.minimalaccounting.application.account;

import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceAccountHoldHandlerTest {
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void successfulCommandPublishesPlacedEvent() {
        ConcurrentAccountRepository accounts = accounts(null);
        List<AccountHoldPlaced> placed = new ArrayList<>();
        PlaceAccountHoldHandler handler = new PlaceAccountHoldHandler(
                accounts, placed::add, ignored -> { });

        handler.handle(command());

        assertEquals(List.of(new AccountHoldPlaced(
                new AccountNumber("ACCOUNT01"), 10, 0)), placed);
    }

    @Test
    void businessRejectionPublishesDeclinedEvent() {
        ConcurrentAccountRepository accounts = accounts(1050L);
        List<AccountHoldDeclined> declined = new ArrayList<>();
        PlaceAccountHoldHandler handler = new PlaceAccountHoldHandler(
                accounts, ignored -> { }, declined::add);

        handler.handle(command());
        handler.handle(command());

        assertEquals(2, declined.size());
        assertEquals(
                1,
                accounts.get(new AccountNumber("ACCOUNT01"))
                        .orElseThrow().getHolds().size());
    }

    @Test
    void unexpectedRuntimeFailurePropagatesForNack() {
        ConcurrentAccountRepository accounts = accounts(null);
        PlaceAccountHoldHandler handler = new PlaceAccountHoldHandler(
                accounts,
                ignored -> {
                    throw new IllegalStateException("topic unavailable");
                },
                ignored -> { });

        assertThrows(IllegalStateException.class, () -> handler.handle(command()));
    }

    @Test
    void retryAfterPublishingFailureDoesNotPlaceTheHoldTwice() {
        ConcurrentAccountRepository accounts = accounts(null);
        List<AccountHoldPlaced> placed = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        PlaceAccountHoldHandler handler = new PlaceAccountHoldHandler(
                accounts,
                event -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new IllegalStateException("topic unavailable");
                    }
                    placed.add(event);
                },
                ignored -> { });

        assertThrows(IllegalStateException.class, () -> handler.handle(command()));
        handler.handle(command());

        assertEquals(List.of(new AccountHoldPlaced(
                new AccountNumber("ACCOUNT01"), 10, 0)), placed);
        assertEquals(
                100,
                accounts.get(new AccountNumber("ACCOUNT01"))
                        .orElseThrow()
                        .getDebitsHeld()
                        .minor());
    }

    @Test
    void latePlacementAfterReleaseIsAcknowledgedWithoutReopeningTheHold() {
        ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();
        Account account = Account.create(
                new AccountNumber("ACCOUNT01"), "account",
                new Money(1000, USD), null, null);
        account.placeHold(10, 0, DebitCredit.DEBIT, new Money(100, USD));
        account.releaseHold(10, 0, DebitCredit.DEBIT, new Money(100, USD));
        accounts.add(account);
        List<AccountHoldPlaced> placed = new ArrayList<>();
        PlaceAccountHoldHandler handler = new PlaceAccountHoldHandler(
                accounts, placed::add, ignored -> { });

        handler.handle(command());

        assertEquals(List.of(), placed);
        assertEquals(
                0,
                accounts.get(new AccountNumber("ACCOUNT01")).orElseThrow()
                        .getDebitsHeld().minor());
    }

    private static ConcurrentAccountRepository accounts(Long maximum) {
        Account account = new Account(
                null,
                new AccountNumber("ACCOUNT01"),
                "account",
                new Money(1000, USD),
                null,
                maximum == null ? null : new Money(maximum, USD),
                List.of());
        ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();
        accounts.add(account);
        return accounts;
    }

    private static PlaceAccountHold command() {
        return new PlaceAccountHold(
                new AccountNumber("ACCOUNT01"),
                10,
                0,
                DebitCredit.DEBIT,
                new Money(100, USD));
    }
}
