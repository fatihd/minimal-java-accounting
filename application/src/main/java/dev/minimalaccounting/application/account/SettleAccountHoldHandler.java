package dev.minimalaccounting.application.account;

import dev.minimalaccounting.core.account.AccountHoldSettled;
import dev.minimalaccounting.core.account.AccountReference;
import dev.minimalaccounting.core.account.SettleAccountHold;
import dev.minimalaccounting.ports.persistence.AccountRepository;
import dev.minimalaccounting.ports.messaging.Handler;
import dev.minimalaccounting.ports.messaging.Sender;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class SettleAccountHoldHandler implements Handler<SettleAccountHold> {
    @NonNull
    private final AccountRepository accounts;
    @NonNull
    private final Sender<AccountHoldSettled> settled;

    @Override
    public void handle(SettleAccountHold command) {
        var account = accounts.get(AccountReference.id(command.accountId())).orElseThrow();
        account.settleHold(
                command.transactionId(), command.effectNumber(),
                command.side(), command.amount());
        accounts.update(account);
        settled.send(new AccountHoldSettled(
                command.accountId(), command.transactionId(), command.effectNumber()));
    }
}
