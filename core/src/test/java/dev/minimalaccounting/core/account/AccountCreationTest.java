package dev.minimalaccounting.core.account;

import dev.minimalaccounting.shared.AccountNumber;
import dev.minimalaccounting.shared.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountCreationTest {
    private Account account;

    @BeforeEach
    void createAccount() {
        account = Account.create(
                new AccountNumber("ACCOUNT01"),
                "account",
                new Money(1000, Currency.getInstance("USD")),
                null,
                null);
    }

    @Test
    void hasNoPersistenceIdentity() {
        assertNull(account.getId());
    }

    @Test
    void hasNoPersistenceRevision() {
        assertNull(account.getRevision());
    }

    @Test
    void initializesDebitHoldsToZero() {
        assertEquals(0, account.getDebitsHeld().minor());
    }

    @Test
    void initializesCreditHoldsToZero() {
        assertEquals(0, account.getCreditsHeld().minor());
    }
}
