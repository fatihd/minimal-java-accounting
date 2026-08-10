package dev.minimalaccounting.application.account;

import dev.minimalaccounting.model.account.Account;
import dev.minimalaccounting.shared.ports.Repository;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.account.AccountHoldReleased;
import dev.minimalaccounting.model.account.ReleaseAccountHold;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.shared.ports.Sender;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ReleaseAccountHoldHandler implements Handler<ReleaseAccountHold> {
    @NonNull
    private final Repository<AccountNumber, Account> accounts;
    @NonNull
    private final Sender<AccountHoldReleased> released;

    @Override
    public void handle(ReleaseAccountHold command) {
        var account = accounts.get(command.accountNumber()).orElseThrow();
        account.releaseHold(
                command.transactionId(), command.side(), command.amount());
        accounts.update(account);
        released.send(new AccountHoldReleased(
                command.accountNumber(), command.transactionId()));
    }
}
