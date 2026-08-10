package dev.minimalaccounting.adapters.messaging.outbox;

import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtemisMessageTest {
    @Test
    void transportsARealBusinessMessage() {
        try (var connection = ArtemisTestBroker.connect();
             var queue = new ArtemisQueue<>(
                     connection,
                     ArtemisTestBroker.uniqueName("business-message"),
                     PlaceAccountHold.class)) {
            PlaceAccountHold expected = new PlaceAccountHold(
                    new AccountNumber("ACCOUNT41"),
                    73,
                    DebitCredit.DEBIT,
                    new Money(1250, Currency.getInstance("USD")));

            queue.send(expected);
            var received = queue.receive(1).getFirst();

            assertEquals(expected, received.message());
            queue.ack(received.messageId());
        }
    }
}
