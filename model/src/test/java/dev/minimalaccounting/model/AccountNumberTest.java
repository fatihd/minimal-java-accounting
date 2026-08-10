package dev.minimalaccounting.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountNumberTest {
    @Test
    void acceptsItsBusinessFormat() {
        assertEquals("ACCOUNT01", new AccountNumber("ACCOUNT01").value());
    }

    @Test
    void rejectsTextOutsideItsBusinessFormat() {
        assertThrows(IllegalArgumentException.class, () -> new AccountNumber("short"));
        assertThrows(IllegalArgumentException.class, () -> new AccountNumber("lowercase1"));
    }

    @Test
    void ordersLexicographicallyByItsValue() {
        assertTrue(new AccountNumber("ACCOUNT01").compareTo(
                new AccountNumber("ACCOUNT02")) < 0);
    }
}
