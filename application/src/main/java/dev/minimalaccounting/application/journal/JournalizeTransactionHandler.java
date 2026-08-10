package dev.minimalaccounting.application.journal;

import dev.minimalaccounting.model.journal.JournalEntry;
import dev.minimalaccounting.model.transaction.Transaction;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.journal.JournalIdentifier;
import dev.minimalaccounting.model.journal.JournalService;
import dev.minimalaccounting.model.journal.JournalizeTransaction;
import dev.minimalaccounting.model.journal.TransactionJournalized;
import dev.minimalaccounting.model.transaction.TransactionIdentifier;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.util.ArrayList;

@RequiredArgsConstructor
public final class JournalizeTransactionHandler implements Handler<JournalizeTransaction> {
    @NonNull
    private final Repository<AccountIdentifier, Account> accounts;
    @NonNull
    private final Repository<TransactionIdentifier, Transaction> transactions;
    @NonNull
    private final Repository<JournalIdentifier, JournalEntry> journals;
    @NonNull
    private final Sender<TransactionJournalized> journalized;
    @NonNull
    private final Clock clock;
    private final JournalService service = new JournalService();

    @Override
    public void handle(JournalizeTransaction command) {
        var transaction = transactions.get(TransactionIdentifier.id(command.transactionId()))
                .orElse(null);
        if (transaction == null) {
            return;
        }
        var existing = journals.get(JournalIdentifier.request(transaction.getRequestId()));
        if (existing.isPresent()) {
            journalized.send(new TransactionJournalized(
                    transaction.getId(), existing.orElseThrow().getId()));
            return;
        }
        ArrayList<Account> values = new ArrayList<>();
        for (var number : transaction.accountNumbers()) {
            values.add(accounts.get(AccountIdentifier.number(number))
                    .orElseThrow(() -> new IllegalStateException(
                            "Account is not available for journalization: "
                                    + number.value())));
        }
        var entry = service.journalize(transaction, values, clock.instant());
        long id = journals.add(entry);
        journalized.send(new TransactionJournalized(transaction.getId(), id));
    }
}
