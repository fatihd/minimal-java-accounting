package dev.minimalaccounting.application.journal;

import dev.minimalaccounting.core.account.Account;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.journal.JournalReference;
import dev.minimalaccounting.core.journal.JournalService;
import dev.minimalaccounting.core.journal.JournalizeTransaction;
import dev.minimalaccounting.core.journal.TransactionJournalized;
import dev.minimalaccounting.core.transaction.TransactionReference;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.persistence.JournalRepository;
import dev.minimalaccounting.ports.messaging.Sender;
import dev.minimalaccounting.ports.persistence.TransactionRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayList;

@RequiredArgsConstructor
public final class JournalizeTransactionHandler implements Handler<JournalizeTransaction> {
    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final TransactionRepository transactions;
    @NonNull
    private final JournalRepository journals;
    @NonNull
    private final Sender<TransactionJournalized> journalized;
    @NonNull
    private final Clock clock;
    private final JournalService service = new JournalService();

    @Override
    public void handle(JournalizeTransaction command) {
        var transaction = transactions.get(TransactionReference.id(command.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var existing = journals.get(JournalReference.request(transaction.getRequestId()));
        if (existing.isPresent()) {
            journalized.send(new TransactionJournalized(
                    transaction.getId(), existing.orElseThrow().getId()));
            return;
        }
        ArrayList<Account> values = new ArrayList<>();
        for (var number : transaction.accountNumbers()) {
            values.add(accounts.get(AccountReference.number(number))
                    .orElseThrow(() -> new IllegalStateException(
                            "Account is not available for journalization: "
                                    + number.value())));
        }
        var entry = service.journalize(transaction, values, clock.instant());
        long id = journals.add(entry);
        journalized.send(new TransactionJournalized(transaction.getId(), id));
    }
}
