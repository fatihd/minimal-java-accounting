package dev.minimalaccounting.application.account;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.DebitCredit;
import dev.minimalaccounting.shared.Money;
import dev.minimalaccounting.adapters.persistence.inmemory.ConcurrentAccountRepository;
import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseAccountHoldHandlerTest {
    private static final Currency USD = Currency.getInstance("USD");
    private static final Money AMOUNT = new Money(100, USD);

    @Test
    void duplicateCommandReconfirmsReleaseWithoutChangingTheAccountTwice() {
        ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();
        Account account = Account.create(
                new AccountNumber("ACCOUNT01"), "account",
                new Money(1000, USD), null, null);
        account.placeHold(10, 0, DebitCredit.DEBIT, AMOUNT);
        accounts.add(account);
        List<AccountHoldReleased> events = new ArrayList<>();
        ReleaseAccountHoldHandler handler = new ReleaseAccountHoldHandler(
                accounts, events::add);
        ReleaseAccountHold command = new ReleaseAccountHold(
                1, 10, 0, DebitCredit.DEBIT, AMOUNT);

        handler.handle(command);
        handler.handle(command);

        assertEquals(
                List.of(
                        new AccountHoldReleased(1, 10, 0),
                        new AccountHoldReleased(1, 10, 0)),
                events);
        assertEquals(
                0,
                accounts.get(AccountReference.id(1)).orElseThrow()
                        .getDebitsHeld().minor());
    }

    @Test
    void commandBeforePlacementPropagatesForNack() {
        ConcurrentAccountRepository accounts = new ConcurrentAccountRepository();
        accounts.add(Account.create(
                new AccountNumber("ACCOUNT01"), "account",
                new Money(1000, USD), null, null));
        ReleaseAccountHoldHandler handler = new ReleaseAccountHoldHandler(
                accounts, ignored -> { });

        assertThrows(
                IllegalStateException.class,
                () -> handler.handle(new ReleaseAccountHold(
                        1, 10, 0, DebitCredit.DEBIT, AMOUNT)));
    }
}
