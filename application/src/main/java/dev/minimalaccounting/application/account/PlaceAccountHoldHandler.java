package dev.minimalaccounting.application.account;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.PostingRejectedException;
import dev.minimalaccounting.model.account.AccountHoldDeclined;
import dev.minimalaccounting.model.account.AccountHoldPlaced;
import dev.minimalaccounting.model.account.AccountHoldState;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.account.PlaceAccountHold;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class PlaceAccountHoldHandler implements Handler<PlaceAccountHold> {
    @NonNull
    private final Repository<AccountIdentifier, Account> accounts;
    @NonNull
    private final Sender<AccountHoldPlaced> placed;
    @NonNull
    private final Sender<AccountHoldDeclined> declined;

    @Override
    public void handle(PlaceAccountHold command) {
        var account = accounts.get(AccountIdentifier.id(command.accountId())).orElseThrow();
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
