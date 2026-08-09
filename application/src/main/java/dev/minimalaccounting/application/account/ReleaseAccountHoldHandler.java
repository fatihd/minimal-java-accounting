package dev.minimalaccounting.application.account;

import dev.minimalaccounting.core.account.AccountHoldReleased;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.account.ReleaseAccountHold;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ReleaseAccountHoldHandler implements Handler<ReleaseAccountHold> {
    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final Sender<AccountHoldReleased> released;

    @Override
    public void handle(ReleaseAccountHold command) {
        var account = accounts.get(AccountReference.id(command.accountId())).orElseThrow();
        account.releaseHold(
                command.transactionId(), command.effectNumber(),
                command.side(), command.amount());
        accounts.update(account);
        released.send(new AccountHoldReleased(
                command.accountId(), command.transactionId(), command.effectNumber()));
    }
}
