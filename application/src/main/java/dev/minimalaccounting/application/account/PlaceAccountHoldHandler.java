package dev.minimalaccounting.application.account;

import dev.minimalaccounting.shared.PostingRejectedException;
import dev.minimalaccounting.core.account.AccountHoldDeclined;
import dev.minimalaccounting.core.account.AccountHoldPlaced;
import dev.minimalaccounting.core.account.AccountHoldState;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.account.PlaceAccountHold;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PlaceAccountHoldHandler implements Handler<PlaceAccountHold> {
    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final Sender<AccountHoldPlaced> placed;
    @NonNull
    private final Sender<AccountHoldDeclined> declined;

    @Override
    public void handle(PlaceAccountHold command) {
        var account = accounts.get(AccountReference.id(command.accountId())).orElseThrow();
        try {
            AccountHoldState state = account.placeHold(
                    command.transactionId(), command.effectNumber(),
                    command.side(), command.amount());
            if (state != AccountHoldState.HELD) {
                return;
            }
            accounts.update(account);
            placed.send(new AccountHoldPlaced(
                    command.accountId(), command.transactionId(), command.effectNumber()));
        } catch (PostingRejectedException rejection) {
            accounts.update(account);
            declined.send(new AccountHoldDeclined(
                    command.accountId(), command.transactionId(), command.effectNumber(),
                    rejection.getMessage()));
        }
    }
}
