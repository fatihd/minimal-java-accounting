package dev.minimalaccounting.application.account;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.AccountIdentifier;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ReleaseAccountHoldHandler implements Handler<ReleaseAccountHold> {
    @NonNull
    private final Repository<AccountIdentifier, Account> accounts;
    @NonNull
    private final Sender<AccountHoldReleased> released;

    @Override
    public void handle(ReleaseAccountHold command) {
        var account = accounts.get(AccountIdentifier.id(command.accountId())).orElseThrow();
        account.releaseHold(
                command.transactionId(), command.effectNumber(),
                command.side(), command.amount());
        accounts.update(account);
        released.send(new AccountHoldReleased(
                command.accountId(), command.transactionId(), command.effectNumber()));
    }
}
